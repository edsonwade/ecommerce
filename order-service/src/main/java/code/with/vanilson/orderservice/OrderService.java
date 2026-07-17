package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerInfo;
import code.with.vanilson.orderservice.customer.CustomerSnapshot;
import code.with.vanilson.orderservice.customer.CustomerSnapshotRepository;
import code.with.vanilson.orderservice.exception.CustomerNotFoundException;
import code.with.vanilson.orderservice.exception.CustomerServiceUnavailableException;
import code.with.vanilson.orderservice.exception.OrderDuplicateReferenceException;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderIllegalStateTransitionException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.exception.OrderValidationException;
import code.with.vanilson.orderservice.event.OrderStatusChangedEvent;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private static final String METRIC_CUSTOMER_RESOLVE = "customer.resolve.count";

    private final OrderRepository              orderRepository;
    private final OrderMapper                  orderMapper;
    private final CustomerClient               customerClient;
    private final CustomerSnapshotRepository   snapshotRepository;
    private final OrderLineService             orderLineService;
    private final OutboxRepository             outboxRepository;
    private final MessageSource                messageSource;
    private final ObjectMapper                 objectMapper;
    private final TenantHibernateFilterActivator filterActivator;
    private final MeterRegistry                meterRegistry;
    private final ApplicationEventPublisher    eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        OrderMapper orderMapper,
                        CustomerClient customerClient,
                        CustomerSnapshotRepository snapshotRepository,
                        OrderLineService orderLineService,
                        OutboxRepository outboxRepository,
                        MessageSource messageSource,
                        TenantHibernateFilterActivator filterActivator,
                        MeterRegistry meterRegistry,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository    = orderRepository;
        this.orderMapper        = orderMapper;
        this.customerClient     = customerClient;
        this.snapshotRepository = snapshotRepository;
        this.orderLineService   = orderLineService;
        this.outboxRepository   = outboxRepository;
        this.messageSource      = messageSource;
        this.filterActivator    = filterActivator;
        this.meterRegistry      = meterRegistry;
        this.eventPublisher     = eventPublisher;
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
        return createOrder(request, null);
    }

    /**
     * Creates an order, with optional client-supplied idempotency.
     * <p>
     * The {@code idempotencyKey} (sent by the client as the {@code Idempotency-Key}
     * header) makes order creation safe to retry. Without it, a <em>false</em> 503 at
     * the gateway — caused by a stale keep-alive connection on a write that actually
     * succeeded — let a user's resubmit create a <strong>second</strong> order
     * (observed in live QA as a duplicated order). When a key is supplied we reuse it
     * as the order's {@code correlationId} (already {@code unique}, so no new schema):
     * if an order with that key already exists for this tenant, we return its
     * correlationId unchanged instead of creating a duplicate. The DB unique constraint
     * on {@code correlationId} is the final backstop against a concurrent double-submit.
     *
     * @param request        validated order request
     * @param idempotencyKey client-generated key, stable across retries of one checkout; may be null
     * @return correlationId for the client to poll status
     */
    @Transactional
    public String createOrder(OrderRequest request, String idempotencyKey) {
        // Derive customerId from the authenticated JWT principal, not the request body.
        // The principal's userId (Long) is the string key used as the MongoDB customer _id.
        // This prevents spoofing where user A sends user B's customerId.
        String customerId = resolveCustomerId(request);
        log.info(msg("order.log.creating", customerId, request.products().size()));

        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();
        String correlationId = hasIdempotencyKey ? idempotencyKey : UUID.randomUUID().toString();

        // Idempotent replay: if this key already produced an order for this tenant,
        // return that order rather than creating a second one.
        if (hasIdempotencyKey) {
            filterActivator.activateFilter();
            String tenantId = TenantContext.requireCurrentTenantId();
            var existing = orderRepository.findByCorrelationIdAndTenantId(correlationId, tenantId);
            if (existing.isPresent()) {
                log.info("[OrderService] Idempotent replay — returning existing order: "
                                + "correlationId=[{}] orderId=[{}]",
                        correlationId, existing.get().getOrderId());
                return correlationId;
            }
        }

        // Step 1 — Validate customer (snapshot-first, Feign fallback)
        CustomerInfo customer = resolveCustomer(customerId);

        log.info(msg("order.log.customer.found", customer.customerId()));

        // Step 2 — Persist order in REQUESTED state.
        // Pass the authenticated customerId so the saved order is keyed by the SAME id
        // that findMyOrders() later queries by (principal.userId()). Without this the
        // mapper stores request.customerId() (client-supplied) while history reads by
        // the principal — if they ever diverge, the order is created but never appears
        // in the user's history. Forcing it here also closes the spoofing hole.
        Order order = persistOrderWithLines(request, correlationId, customerId);

        log.info(msg("order.log.order.saved", order.getOrderId(), order.getReference()));

        // Step 3 — Persist OutboxEvent (same transaction — atomic!)
        OrderRequestedEvent event = buildOrderRequestedEvent(
                request, customer, correlationId, order.getReference(), order.getTenantId(), order.getOrderId());
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
            OrderStatus current = order.getStatus();

            // Idempotent no-op: at-least-once Kafka delivery may replay the same update.
            if (current == newStatus) {
                log.info("[OrderService] Status already {} — skipping duplicate update: correlationId=[{}]",
                        newStatus, correlationId);
                return;
            }

            if (!current.canTransitionTo(newStatus)) {
                throw new OrderIllegalStateTransitionException(
                        msg("order.status.transition.invalid", current, newStatus, correlationId),
                        "order.status.transition.invalid");
            }

            log.info("[OrderService] Status update: correlationId=[{}] {} → {}",
                    correlationId, current, newStatus);
            order.setStatus(newStatus);
            orderRepository.save(order);
        });
    }

    // -------------------------------------------------------
    // REFUND (Fase 6) — driven by payment.refunded, consumed by PaymentRefundConsumer
    // -------------------------------------------------------

    /**
     * Applies a payment refund to the owning order: transitions it to {@code REFUNDED} and
     * writes an {@code order.refunded} outbox row in the SAME transaction (outbox pattern —
     * product-service's restock consumer picks it up from there).
     * <p>
     * {@code PaymentRefundedEvent} carries neither {@code correlationId} nor {@code tenantId}
     * (payment-service stores neither), so the order is looked up by its own PK
     * ({@code orderId}) and both values are read off the found row.
     * <p>
     * Idempotent: a redelivered event on an already-REFUNDED order is a no-op (no second
     * outbox row). Failure semantics (documented, Fase 6 basic scope): if the order can't
     * transition (e.g. already CANCELLED), this throws and the caller
     * ({@code PaymentRefundConsumer}) does NOT acknowledge — Kafka retries, then DLQs. The
     * payment stays REFUNDED regardless; reconciling that split is out of basic scope.
     *
     * @param orderId    the order's internal PK (Payment.orderId — NOT the correlationId)
     * @param eventId    the source PaymentRefundedEvent's eventId (becomes the outbox eventId)
     * @param occurredAt when payment-service processed the refund
     */
    @Transactional
    public void applyRefund(Integer orderId, String eventId, Instant occurredAt) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(
                        msg("order.not.found", orderId), "order.not.found"));

        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.info("[OrderService] Order already REFUNDED — skipping duplicate refund: orderId=[{}]", orderId);
            return;
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            throw new OrderIllegalStateTransitionException(
                    msg("order.status.transition.invalid", order.getStatus(), OrderStatus.REFUNDED, order.getCorrelationId()),
                    "order.status.transition.invalid");
        }

        order.setStatus(OrderStatus.REFUNDED);
        Order saved = orderRepository.save(order);

        persistOrderRefundedOutboxEvent(saved, eventId, occurredAt);

        log.info("[OrderService] Order REFUNDED: orderId=[{}] correlationId=[{}]",
                orderId, saved.getCorrelationId());
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                saved.getCorrelationId(), OrderStatus.REFUNDED.name(), saved.getReference(), Instant.now()));
    }

    private void persistOrderRefundedOutboxEvent(Order order, String sourceEventId, Instant occurredAt) {
        code.with.vanilson.orderservice.kafka.OrderRefundedEvent event =
                new code.with.vanilson.orderservice.kafka.OrderRefundedEvent(
                        sourceEventId, order.getCorrelationId(), order.getReference(), occurredAt, 1);
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                    .eventId(sourceEventId)
                    .correlationId(order.getCorrelationId())
                    .tenantId(order.getTenantId())
                    .topic("order.refunded")
                    .payload(payload)
                    .partitionKey(order.getCorrelationId())
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .build();
            outboxRepository.save(outbox);
        } catch (JsonProcessingException ex) {
            log.error("[OrderService] Failed to serialise OrderRefundedEvent: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to create outbox event for refund: " + order.getCorrelationId(), ex);
        }
    }

    // -------------------------------------------------------
    // MANUAL FULFILLMENT STATUS UPDATE (Fase 5) — seller/admin driven
    // -------------------------------------------------------

    /**
     * Advances an order's fulfillment status via the manual endpoint
     * ({@code PATCH /api/v1/orders/{id}/status}). Distinct from {@link #updateStatus} (the
     * Kafka saga path): this is caller-authorised and keyed by the internal {@code orderId}.
     * <p>
     * Rules:
     * <ul>
     *   <li><b>Whitelist</b> — only {@code SHIPPED} / {@code DELIVERED} are settable here; the saga
     *       remains the only path to CONFIRMED/CANCELLED and refunds arrive via the payment saga
     *       (Fase 6), so any other target → {@code 400 order.status.update.not.allowed}.</li>
     *   <li><b>Authorisation</b> — ADMIN may advance any order; a SELLER must own at least one line
     *       in it ({@code order_line.seller_id}), else {@code 403 order.status.update.forbidden}.</li>
     *   <li><b>Transition</b> — must satisfy {@link OrderStatus#canTransitionTo}
     *       (e.g. DELIVERED before SHIPPED → {@code 409 order.status.transition.invalid}).</li>
     * </ul>
     * Records the fulfillment timestamp and republishes {@link OrderStatusChangedEvent} so the
     * existing SSE stream / notification listeners react exactly as they do for saga updates.
     * Idempotent: re-sending the current status is a no-op that returns the order unchanged.
     */
    @Transactional
    public OrderResponse updateStatusManually(Integer orderId, OrderStatus newStatus) {
        if (newStatus != OrderStatus.SHIPPED && newStatus != OrderStatus.DELIVERED) {
            throw new OrderValidationException(
                    msg("order.status.update.not.allowed", newStatus),
                    "order.status.update.not.allowed");
        }

        filterActivator.activateFilter();
        Order order = (TenantContext.isPresent()
                ? orderRepository.findByOrderIdAndTenantId(orderId, TenantContext.getCurrentTenantId())
                : orderRepository.findById(orderId))
                .orElseThrow(() -> new OrderNotFoundException(
                        msg("order.not.found", orderId), "order.not.found"));

        SecurityPrincipal principal = currentPrincipal();
        if (principal != null && !"ADMIN".equals(principal.role())) {
            boolean sellerOwnsLine = principal.isSeller()
                    && orderLineService.sellerOwnsLineInOrder(orderId, String.valueOf(principal.userId()));
            if (!sellerOwnsLine) {
                throw new OrderForbiddenException(
                        msg("order.status.update.forbidden"), "order.status.update.forbidden");
            }
        }

        OrderStatus current = order.getStatus();
        if (current == newStatus) {
            log.info("[OrderService] Manual status already {} — no-op: orderId=[{}]", newStatus, orderId);
            CustomerSnapshot snap = snapshotRepository.findById(order.getCustomerId()).orElse(null);
            return orderMapper.fromOrder(order, snap);
        }
        if (!current.canTransitionTo(newStatus)) {
            throw new OrderIllegalStateTransitionException(
                    msg("order.status.transition.invalid", current, newStatus, order.getCorrelationId()),
                    "order.status.transition.invalid");
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.SHIPPED) {
            order.setShippedAt(LocalDateTime.now());
        } else {
            order.setDeliveredAt(LocalDateTime.now());
        }
        Order saved = orderRepository.save(order);

        log.info("[OrderService] Manual fulfillment update: orderId=[{}] {} → {}",
                orderId, current, newStatus);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                saved.getCorrelationId(), newStatus.name(), saved.getReference(), Instant.now()));

        CustomerSnapshot snapshot = snapshotRepository.findById(saved.getCustomerId()).orElse(null);
        return orderMapper.fromOrder(saved, snapshot);
    }

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    public List<OrderResponse> findAllOrders() {
        filterActivator.activateFilter();
        List<OrderResponse> orders = toResponses(orderRepository.findAll());
        log.info(msg("order.log.all.orders.found", orders.size()));
        return orders;
    }

    /**
     * Maps orders to invoice responses, enriching each with its customer snapshot.
     * Snapshots are batch-loaded once (findAllById) to avoid an N+1 query across the list.
     */
    private List<OrderResponse> toResponses(List<Order> orders) {
        java.util.Set<String> customerIds = orders.stream()
                .map(Order::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Map<String, CustomerSnapshot> byId = snapshotRepository.findAllById(customerIds)
                .stream()
                .collect(Collectors.toMap(CustomerSnapshot::getCustomerId, s -> s, (a, b) -> a));
        return orders.stream()
                .map(o -> orderMapper.fromOrder(o, byId.get(o.getCustomerId())))
                .collect(Collectors.toList());
    }

    /**
     * Returns the orders that contain at least one of the authenticated seller's
     * products. Marketplace isolation: a seller sees only orders placed for their own
     * products (keyed off the {@code seller_id} stamped on each line at creation), never
     * other sellers' orders. Backs {@code GET /api/v1/orders/seller}.
     */
    public List<OrderResponse> findOrdersForSeller() {
        SecurityPrincipal principal = currentPrincipal();
        if (principal == null) {
            return List.of();
        }
        String sellerId = String.valueOf(principal.userId());
        List<OrderResponse> orders = toResponses(orderLineService.findOrdersForSeller(sellerId));
        log.info("[OrderService] Seller orders for sellerId=[{}]: count=[{}]", sellerId, orders.size());
        return orders;
    }

    public List<OrderResponse> findMyOrders() {
        filterActivator.activateFilter();
        SecurityPrincipal principal = currentPrincipal();
        if (principal == null) {
            return List.of();
        }
        String customerId = String.valueOf(principal.userId());
        List<OrderResponse> orders = toResponses(orderRepository.findByCustomerId(customerId));
        log.info("[OrderService] My orders for customerId=[{}]: count=[{}]", customerId, orders.size());
        return orders;
    }

    public OrderResponse findById(Integer id) {
        filterActivator.activateFilter();
        // Tenant safety: findById (= em.find) bypasses the Hibernate tenant filter by design,
        // so when a tenant is bound we query by (id, tenant) — a caller of tenant A reading
        // tenant B's order gets a 404, not a leak. The ownership guard below only checks the
        // principal, so it cannot enforce tenant isolation on its own. Without a bound tenant
        // (internal/single-tenant paths) fall back to the plain lookup.
        Order order = (TenantContext.isPresent()
                ? orderRepository.findByOrderIdAndTenantId(id, TenantContext.getCurrentTenantId())
                : orderRepository.findById(id))
                .orElseThrow(() -> new OrderNotFoundException(
                        msg("order.not.found", id), "order.not.found"));

        SecurityPrincipal principal = currentPrincipal();
        if (principal != null && !"ADMIN".equals(principal.role())
                && !order.getCustomerId().equals(String.valueOf(principal.userId()))
                && !(principal.isSeller()
                     && orderLineService.sellerOwnsLineInOrder(id, String.valueOf(principal.userId())))) {
            throw new OrderForbiddenException(
                    msg("order.access.denied"), "order.access.denied");
        }

        log.info(msg("order.log.order.found", id));
        CustomerSnapshot snapshot = snapshotRepository.findById(order.getCustomerId()).orElse(null);
        return orderMapper.fromOrder(order, snapshot);
    }

    private SecurityPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityPrincipal sp)) return null;
        return sp;
    }

    private String resolveCustomerId(OrderRequest request) {
        SecurityPrincipal principal = currentPrincipal();
        if (principal != null) {
            return String.valueOf(principal.userId());
        }
        // Fallback: use the request body value (should not happen on authenticated endpoints)
        return request.customerId();
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /**
     * Resolves customer data using a two-tier lookup strategy:
     * <ol>
     *   <li>Check local {@link CustomerSnapshot} table (no network call).</li>
     *   <li>Fall back to Feign call if snapshot not found (new customer, event not yet arrived).</li>
     * </ol>
     * Metric {@code customer.resolve.count} tracks snapshot hits vs Feign fallbacks.
     * When snapshot coverage reaches 99%+, the Feign fallback can be removed.
     */
    private CustomerInfo resolveCustomer(String customerId) {
        Optional<CustomerSnapshot> snapshot = snapshotRepository.findById(customerId);
        if (snapshot.isPresent()) {
            CustomerSnapshot s = snapshot.get();
            log.info("[OrderService] Customer resolved from snapshot: customerId=[{}]", customerId);
            meterRegistry.counter(METRIC_CUSTOMER_RESOLVE, "source", "snapshot").increment();
            return new CustomerInfo(s.getCustomerId(), s.getFirstname(), s.getLastname(), s.getEmail(), null);
        }

        log.info("[OrderService] Customer not in snapshot, falling back to Feign: customerId=[{}]", customerId);
        meterRegistry.counter(METRIC_CUSTOMER_RESOLVE, "source", "feign").increment();
        return customerClient.findCustomerById(customerId)
                .orElseThrow(() -> {
                    log.warn("[OrderService] Customer not found via Feign: customerId=[{}]", customerId);
                    return new CustomerNotFoundException(
                            msg("order.customer.not.found", customerId),
                            "order.customer.not.found");
                });
    }

    @Transactional
    protected Order persistOrderWithLines(OrderRequest request, String correlationId, String customerId) {
        if (request.reference() != null && orderRepository.existsByReference(request.reference())) {
            throw new OrderDuplicateReferenceException(
                    msg("order.reference.duplicate", request.reference()),
                    "order.reference.duplicate");
        }

        Order order = orderMapper.toOrder(request);
        order.setCorrelationId(correlationId);
        // Override the mapper's request-body customerId with the authenticated id so
        // the persisted owner matches exactly what findMyOrders() filters on.
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.REQUESTED);
        order.setTenantId(TenantContext.requireCurrentTenantId());
        if (order.getReference() == null || order.getReference().isBlank()) {
            order.setReference("ORD-" + correlationId.replace("-", "").substring(0, 10).toUpperCase());
        }
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
                                                          String correlationId,
                                                          String orderReference,
                                                          String tenantId,
                                                          Integer orderId) {
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
                // Use the PERSISTED order reference, not request.reference():
                // the UI never sends a reference, so request.reference() is null.
                // A null here propagates through inventory.reserved into the
                // payment.order_reference NOT NULL column → payment always fails.
                orderReference,
                // tenantId + orderId carried through the saga so payment-service can
                // satisfy its NOT NULL payment.tenant_id / payment.order_id columns —
                // the Kafka consumer has no HTTP TenantContext and no order PK otherwise.
                tenantId,
                orderId,
                Instant.now(),
                1                                // schemaVersion
        );
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
