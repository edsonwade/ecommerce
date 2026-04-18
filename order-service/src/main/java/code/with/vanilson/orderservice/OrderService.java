package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerInfo;
import code.with.vanilson.orderservice.exception.CustomerServiceUnavailableException;
import code.with.vanilson.orderservice.exception.OrderDuplicateReferenceException;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import code.with.vanilson.orderservice.kafka.OrderRequestedEvent;
import code.with.vanilson.orderservice.orderLine.OrderLineRequest;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.outbox.OutboxEvent;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.product.ProductPurchaseRequest;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OrderService — Application Layer (Phase 4 — Multi-Tenancy)
 * <p>
 * Phase 4 additions:
 * - TenantContext.requireCurrentTenantId() sets tenantId on every new Order and OutboxEvent
 * - TenantHibernateFilterActivator enables per-tenant filtering on all read operations
 * - All Feign calls propagate X-Tenant-ID automatically via TenantFeignInterceptor
 * <p>
 * Phase 3 architecture (unchanged):
 * - createOrder() is fully async: save order + OutboxEvent → return 202 → saga via Kafka
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@Service
public class OrderService {

    private static final String TOPIC_ORDER_REQUESTED = "order.requested";

    private final OrderRepository              orderRepository;
    private final OrderMapper                  orderMapper;
    private final CustomerClient               customerClient;
    private final OrderLineService             orderLineService;
    private final OutboxRepository             outboxRepository;
    private final MessageSource                messageSource;
    private final ObjectMapper                 objectMapper;
    private final TenantHibernateFilterActivator filterActivator;

    public OrderService(OrderRepository orderRepository,
                        OrderMapper orderMapper,
                        CustomerClient customerClient,
                        OrderLineService orderLineService,
                        OutboxRepository outboxRepository,
                        MessageSource messageSource,
                        TenantHibernateFilterActivator filterActivator) {
        this.orderRepository  = orderRepository;
        this.orderMapper      = orderMapper;
        this.customerClient   = customerClient;
        this.orderLineService = orderLineService;
        this.outboxRepository = outboxRepository;
        this.messageSource    = messageSource;
        this.filterActivator  = filterActivator;
        // ObjectMapper with JSR310 for Instant serialisation
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // -------------------------------------------------------
    // CREATE ORDER — async, returns 202 + correlationId
    // -------------------------------------------------------

    /**
     * Creates an order asynchronously.
     * <p>
     * Single DB transaction:
     * - Validate customer (sync HTTP — fast)
     * - Persist order (REQUESTED state)
     * - Persist OutboxEvent (serialised OrderRequestedEvent)
     * - Return correlationId to client immediately
     * <p>
     * The OutboxEventPublisher (scheduled every 5s) will pick up
     * the OutboxEvent and publish to Kafka — saga proceeds from there.
     *
     * @param request validated order request
     * @return correlationId for client to poll status
     */
    @Transactional
    public String createOrder(OrderRequest request) {
        log.info(msg("order.log.creating", request.customerId(), request.products().size()));

        // Step 1 — Validate customer (sync, cached — typically < 5ms)
        CustomerInfo customer = customerClient.findCustomerById(request.customerId())
                .orElseThrow(() -> {
                    log.warn("[OrderService] Customer not found: customerId=[{}]", request.customerId());
                    return new CustomerServiceUnavailableException(
                            msg("order.customer.not.found", request.customerId()),
                            "order.customer.not.found");
                });

        log.info(msg("order.log.customer.found", customer.customerId()));

        // Step 2 — Persist order in REQUESTED state
        String correlationId = UUID.randomUUID().toString();
        Order order = persistOrderWithLines(request, correlationId);

        log.info(msg("order.log.order.saved", order.getOrderId(), order.getReference()));

        // Step 3 — Persist OutboxEvent (same transaction — atomic!)
        OrderRequestedEvent event = buildOrderRequestedEvent(request, customer, correlationId);
        persistOutboxEvent(event, correlationId);

        log.info("[OrderService] Order persisted in REQUESTED state. correlationId=[{}] — saga will proceed via Kafka",
                correlationId);

        return correlationId; // returned as 202 Accepted body
    }

    // -------------------------------------------------------
    // STATUS POLLING — client polls this after 202
    // -------------------------------------------------------

    /**
     * Returns the current saga status of an order identified by correlationId.
     * Clients poll this after receiving 202 Accepted on createOrder.
     *
     * @param correlationId the correlation ID returned by createOrder
     * @return OrderStatusResponse with current status
     */
    public OrderStatusResponse getOrderStatus(String correlationId) {
        filterActivator.activateFilter();
        String tenantId = TenantContext.requireCurrentTenantId();
        return orderRepository.findByCorrelationIdAndTenantId(correlationId, tenantId)
                .map(order -> {
                    log.info("[OrderService] Status query: correlationId=[{}] status=[{}]",
                            correlationId, order.getStatus());
                    return new OrderStatusResponse(
                            order.getOrderId(),
                            order.getCorrelationId(),
                            order.getReference(),
                            order.getStatus().name(),
                            order.getTotalAmount(),
                            order.getCreatedDate()
                    );
                })
                .orElseThrow(() -> new OrderNotFoundException(
                        msg("order.not.found", correlationId),
                        "order.not.found"));
    }

    // -------------------------------------------------------
    // SAGA STATUS UPDATES — called by OrderSagaConsumer
    // -------------------------------------------------------

    @Transactional
    public void updateStatus(String correlationId, OrderStatus newStatus) {
        orderRepository.findByCorrelationId(correlationId).ifPresent(order -> {
            log.info("[OrderService] Status update: correlationId=[{}] {} → {}",
                    correlationId, order.getStatus(), newStatus);
            order.setStatus(newStatus);
            orderRepository.save(order);
        });
    }

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    public List<OrderResponse> findAllOrders() {
        filterActivator.activateFilter();
        List<OrderResponse> orders = orderRepository.findAll()
                .stream()
                .map(orderMapper::fromOrder)
                .collect(Collectors.toList());
        log.info(msg("order.log.all.orders.found", orders.size()));
        return orders;
    }

    public OrderResponse findById(Integer id) {
        filterActivator.activateFilter();
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(
                        msg("order.not.found", id), "order.not.found"));

        SecurityPrincipal principal = currentPrincipal();
        if (principal != null && !"ADMIN".equals(principal.role())
                && !order.getCustomerId().equals(String.valueOf(principal.userId()))) {
            throw new OrderForbiddenException(
                    msg("order.access.denied"), "order.access.denied");
        }

        log.info(msg("order.log.order.found", id));
        return orderMapper.fromOrder(order);
    }

    private SecurityPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityPrincipal sp)) return null;
        return sp;
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    @Transactional
    protected Order persistOrderWithLines(OrderRequest request, String correlationId) {
        if (request.reference() != null && orderRepository.existsByReference(request.reference())) {
            throw new OrderDuplicateReferenceException(
                    msg("order.reference.duplicate", request.reference()),
                    "order.reference.duplicate");
        }

        Order order = orderMapper.toOrder(request);
        order.setCorrelationId(correlationId);
        order.setStatus(OrderStatus.REQUESTED);
        order.setTenantId(TenantContext.requireCurrentTenantId());
        Order saved = orderRepository.save(order);

        request.products().forEach(p -> orderLineService.saveOrderLine(
                new OrderLineRequest(null, saved.getOrderId(), p.productId(), p.quantity())));

        return saved;
    }

    private void persistOutboxEvent(OrderRequestedEvent event, String correlationId) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventId(event.eventId())
                    .correlationId(correlationId)
                    .tenantId(TenantContext.requireCurrentTenantId())
                    .topic(TOPIC_ORDER_REQUESTED)
                    .payload(payload)
                    .partitionKey(correlationId)
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .build();
            outboxRepository.save(outbox);
        } catch (JsonProcessingException ex) {
            log.error("[OrderService] Failed to serialise OrderRequestedEvent: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to create outbox event for order: " + correlationId, ex);
        }
    }

    private OrderRequestedEvent buildOrderRequestedEvent(OrderRequest request,
                                                          CustomerInfo customer,
                                                          String correlationId) {
        List<ProductPurchaseRequest> products = request.products().stream()
                .map(p -> new ProductPurchaseRequest(p.productId(), p.quantity()))
                .collect(Collectors.toList());

        return new OrderRequestedEvent(
                UUID.randomUUID().toString(),    // eventId
                correlationId,
                customer.customerId(),
                customer.email(),
                customer.firstname(),
                customer.lastname(),
                products,
                request.amount(),
                request.paymentMethod(),
                request.reference(),
                Instant.now(),
                1                                // schemaVersion
        );
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
