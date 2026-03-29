package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerInfo;
import code.with.vanilson.orderservice.exception.CustomerServiceUnavailableException;
import code.with.vanilson.orderservice.exception.OrderDuplicateReferenceException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.kafka.OrderRequestedEvent;
import code.with.vanilson.orderservice.orderLine.OrderLineRequest;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.outbox.OutboxEvent;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.product.ProductPurchaseRequest;
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
 * OrderService — Application Layer (Phase 3 — Async/Event-Driven)
 * <p>
 * MAJOR CHANGE FROM PHASE 1:
 * <p>
 * Phase 1: createOrder() made 3 synchronous HTTP calls (customer + product + payment).
 * If any service was slow → order throughput collapsed.
 * <p>
 * Phase 3: createOrder() is now fully async:
 * 1. Validate customer (still sync — fast Feign call, cached in gateway)
 * 2. Save order in REQUESTED state + OutboxEvent in ONE DB transaction
 * 3. Return 202 Accepted + correlationId immediately (< 10ms response time)
 * 4. OutboxEventPublisher picks up the OutboxEvent and publishes to order.requested topic
 * 5. product-service consumes → reserves inventory → publishes inventory.reserved
 * 6. payment-service consumes → processes payment → publishes payment.authorized
 * 7. order-service consumes → marks order CONFIRMED → sends notification email
 * <p>
 * Benefits:
 * - Black Friday: 100k orders/second → Kafka absorbs all → consumers scale independently
 * - Product-service slow: doesn't block the HTTP response
 * - Payment-service down: order is persisted, processed when service recovers from DLQ
 * - Zero data loss: Outbox pattern guarantees at-least-once Kafka delivery
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Slf4j
@Service
public class OrderService {

    private static final String TOPIC_ORDER_REQUESTED = "order.requested";

    private final OrderRepository  orderRepository;
    private final OrderMapper      orderMapper;
    private final CustomerClient   customerClient;
    private final OrderLineService orderLineService;
    private final OutboxRepository outboxRepository;
    private final MessageSource    messageSource;
    private final ObjectMapper     objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OrderMapper orderMapper,
                        CustomerClient customerClient,
                        OrderLineService orderLineService,
                        OutboxRepository outboxRepository,
                        MessageSource messageSource) {
        this.orderRepository  = orderRepository;
        this.orderMapper      = orderMapper;
        this.customerClient   = customerClient;
        this.orderLineService = orderLineService;
        this.outboxRepository = outboxRepository;
        this.messageSource    = messageSource;
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
        return orderRepository.findByCorrelationId(correlationId)
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
        List<OrderResponse> orders = orderRepository.findAll()
                .stream()
                .map(orderMapper::fromOrder)
                .collect(Collectors.toList());
        log.info(msg("order.log.all.orders.found", orders.size()));
        return orders;
    }

    public OrderResponse findById(Integer id) {
        return orderRepository.findById(id)
                .map(order -> {
                    log.info(msg("order.log.order.found", id));
                    return orderMapper.fromOrder(order);
                })
                .orElseThrow(() -> new OrderNotFoundException(
                        msg("order.not.found", id), "order.not.found"));
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
