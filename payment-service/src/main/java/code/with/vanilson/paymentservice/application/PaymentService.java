package code.with.vanilson.paymentservice.application;

import code.with.vanilson.paymentservice.domain.Payment;
import code.with.vanilson.paymentservice.domain.PaymentStatus;
import code.with.vanilson.paymentservice.exception.PaymentConflictException;
import code.with.vanilson.paymentservice.exception.PaymentNotFoundException;
import code.with.vanilson.paymentservice.infrastructure.kafka.PaymentRefundedEvent;
import code.with.vanilson.paymentservice.infrastructure.messaging.NotificationProducer;
import code.with.vanilson.paymentservice.infrastructure.messaging.PaymentNotificationRequest;
import code.with.vanilson.paymentservice.infrastructure.outbox.PaymentOutboxEvent;
import code.with.vanilson.paymentservice.infrastructure.outbox.PaymentOutboxRepository;
import code.with.vanilson.paymentservice.infrastructure.repository.PaymentRepository;
import code.with.vanilson.tenantcontext.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * PaymentService — Application Layer
 * <p>
 * Core business logic for payment processing.
 * <p>
 * KEY CHANGES FROM ORIGINAL (critical production fixes):
 * <p>
 * 1. IDEMPOTENCY (FIX FOR FLAW 4 — double charge prevention):
 *    Before persisting a new payment, the service checks if a payment for the
 *    same orderReference already exists (via idempotencyKey lookup).
 *    If found → returns the existing payment without charging again.
 *    This is the standard idempotency pattern used by Stripe and Adyen.
 * <p>
 * 2. SINGLE RESPONSIBILITY (SOLID-S):
 *    PaymentService handles only business orchestration.
 *    Kafka publishing is delegated to NotificationProducer.
 *    DB access is delegated to PaymentRepository.
 *    Mapping is delegated to PaymentMapper.
 * <p>
 * 3. DEPENDENCY INVERSION (SOLID-D):
 *    Depends on abstractions (Repository interface, MessageSource),
 *    not on concrete implementations.
 * <p>
 * 4. @Transactional scope:
 *    Only wraps DB operations. Kafka publish is outside the transaction
 *    to avoid holding a DB connection while waiting for the broker.
 * <p>
 * 5. All messages from messages.properties — no hardcoded strings.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class PaymentService {

    private static final String TOPIC_REFUNDED = "payment.refunded";

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final NotificationProducer notificationProducer;
    private final MessageSource messageSource;
    private final PaymentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentMapper paymentMapper,
                          NotificationProducer notificationProducer,
                          MessageSource messageSource,
                          PaymentOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.notificationProducer = notificationProducer;
        this.messageSource = messageSource;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a payment with full idempotency protection.
     * <p>
     * Idempotency algorithm:
     * 1. Derive idempotency key from orderReference (stable, deterministic)
     * 2. Check DB for an existing payment with this key
     * 3a. If found → log duplicate detection, return existing payment ID (NO double charge)
     * 3b. If not found → persist new payment, publish Kafka notification
     * <p>
     * This guarantees that retrying the same payment request (network timeout,
     * client retry, duplicate Feign call) is safe — only one charge ever happens.
     *
     * @param request validated payment request from order-service
     * @return the payment ID (existing or newly created)
     */
    @Transactional
    public Integer createPayment(PaymentRequest request) {
        String idempotencyKey = buildIdempotencyKey(request.orderReference());

        log.info(messageSource.getMessage(
                "payment.log.processing",
                new Object[]{request.orderId(), request.orderReference(),
                        request.amount(), request.paymentMethod()},
                LocaleContextHolder.getLocale()));

        // --- Idempotency check ---
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existingPayment -> {
                    // Duplicate detected — return existing result without charging again
                    log.warn(messageSource.getMessage(
                            "payment.idempotency.duplicate.detected",
                            new Object[]{idempotencyKey, request.orderReference()},
                            LocaleContextHolder.getLocale()));
                    return existingPayment.getPaymentId();
                })
                .orElseGet(() -> {
                    // First occurrence — process and persist
                    log.info(messageSource.getMessage(
                            "payment.idempotency.new.processing",
                            new Object[]{idempotencyKey, request.orderReference()},
                            LocaleContextHolder.getLocale()));
                    return processNewPayment(request, idempotencyKey);
                });
    }

    /**
     * Returns all payments.
     */
    public List<PaymentResponse> findAllPayments() {
        List<PaymentResponse> payments = paymentRepository.findAll()
                .stream()
                .map(paymentMapper::toResponse)
                .collect(Collectors.toList());

        log.info(messageSource.getMessage(
                "payment.log.all.found",
                new Object[]{payments.size()},
                LocaleContextHolder.getLocale()));

        return payments;
    }

    /**
     * Returns a single payment by ID.
     *
     * @param paymentId the payment ID
     * @return PaymentResponse DTO
     * @throws PaymentNotFoundException if not found
     */
    public PaymentResponse findById(Integer paymentId) {
        return paymentRepository.findById(paymentId)
                .map(payment -> {
                    log.info(messageSource.getMessage(
                            "payment.log.found.by.id",
                            new Object[]{paymentId},
                            LocaleContextHolder.getLocale()));
                    return paymentMapper.toResponse(payment);
                })
                .orElseThrow(() -> {
                    String message = messageSource.getMessage(
                            "payment.not.found",
                            new Object[]{paymentId},
                            LocaleContextHolder.getLocale());
                    return new PaymentNotFoundException(message, "payment.not.found");
                });
    }

    /**
     * Refunds a payment (Fase 6 — basic refunds). ADMIN-triggered via
     * {@code POST /api/v1/payments/{payment-id}/refund}.
     * <p>
     * Guards:
     * <ul>
     *   <li>404 {@code payment.refund.not.found} — no such payment.</li>
     *   <li>409 {@code payment.refund.invalid.status} — already REFUNDED (refund is one-shot;
     *       a second attempt is rejected before any write, so no CHECK constraint is needed).</li>
     * </ul>
     * On success: sets {@code REFUNDED}, saves, and writes a {@code payment.refunded}
     * row to the transactional outbox <b>in the same transaction</b>. The HTTP request
     * no longer blocks on a Kafka send — {@code PaymentOutboxPublisher} drains the row to
     * the broker off the request thread (Fase 6.1: fixes the ~10s refund; the first-send
     * producer init/metadata cost now lands on the scheduler, never on the user).
     * order-service is the single authority on order status; this service only records
     * that the payment side is done.
     *
     * @param paymentId the payment to refund
     * @return the updated PaymentResponse (status REFUNDED)
     */
    @Transactional
    public PaymentResponse refundPayment(Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        messageSource.getMessage("payment.refund.not.found",
                                new Object[]{paymentId}, LocaleContextHolder.getLocale()),
                        "payment.refund.not.found"));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentConflictException(
                    messageSource.getMessage("payment.refund.invalid.status",
                            new Object[]{paymentId}, LocaleContextHolder.getLocale()),
                    "payment.refund.invalid.status");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        Payment saved = paymentRepository.save(payment);

        log.info(messageSource.getMessage("payment.log.refunded",
                new Object[]{saved.getPaymentId(), saved.getOrderReference()},
                LocaleContextHolder.getLocale()));

        writeRefundOutbox(saved);

        return paymentMapper.toResponse(saved);
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /**
     * Persists a new payment entity and publishes the Kafka notification.
     * Called only when idempotency check confirms this is a first-time request.
     */
    private Integer processNewPayment(PaymentRequest request, String idempotencyKey) {
        Payment payment = paymentMapper.toPayment(request);
        payment.setIdempotencyKey(idempotencyKey);
        // payment.tenant_id is NOT NULL. The tenant is taken from TenantContext —
        // seeded from the saga event by PaymentSagaConsumer, or by the gateway's
        // tenant filter on the direct HTTP path. Mirrors order-service's approach.
        payment.setTenantId(TenantContext.requireCurrentTenantId());

        Payment savedPayment = paymentRepository.save(payment);

        log.info(messageSource.getMessage(
                "payment.log.saved",
                new Object[]{savedPayment.getPaymentId(), savedPayment.getOrderId(),
                        savedPayment.getAmount()},
                LocaleContextHolder.getLocale()));

        // Kafka publish is outside @Transactional boundary intentionally:
        // DB commit happens first, then we publish. If Kafka fails, DB is committed —
        // the outbox pattern (Phase 3) will handle guaranteed delivery.
        publishPaymentNotification(request, savedPayment);

        return savedPayment.getPaymentId();
    }

    /**
     * Builds a deterministic idempotency key from the order reference.
     * Key format: "payment:{orderReference}"
     * Stable across retries — same input always produces same key.
     */
    private String buildIdempotencyKey(String orderReference) {
        String key = "payment:" + orderReference;
        log.debug(messageSource.getMessage(
                "payment.idempotency.key.generated",
                new Object[]{key, orderReference},
                LocaleContextHolder.getLocale()));
        return key;
    }

    /**
     * Builds the Kafka notification payload and delegates publishing to NotificationProducer.
     */
    private void publishPaymentNotification(PaymentRequest request, Payment savedPayment) {
        PaymentNotificationRequest notification = new PaymentNotificationRequest(
                request.orderReference(),
                savedPayment.getAmount(),
                savedPayment.getPaymentMethod().name(),
                request.customer().firstname(),
                request.customer().lastname(),
                request.customer().email()
        );
        notificationProducer.sendNotification(notification);
    }

    /**
     * Writes a {@code payment.refunded} row to the transactional outbox, in the caller's
     * {@code @Transactional} boundary (atomic with the status change). Keyed by
     * orderReference (same partition-key convention as the saga events, so events for the
     * same order stay ordered). {@code PaymentOutboxPublisher} publishes it to Kafka
     * asynchronously — the HTTP request does not wait for the broker.
     */
    private void writeRefundOutbox(Payment payment) {
        String eventId = UUID.randomUUID().toString();
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                eventId,
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getOrderReference(),
                payment.getAmount(),
                Instant.now(),
                1
        );
        try {
            String payload = objectMapper.writeValueAsString(event);
            PaymentOutboxEvent outbox = PaymentOutboxEvent.builder()
                    .eventId(eventId)
                    .correlationId(payment.getOrderReference())
                    .tenantId(payment.getTenantId())
                    .topic(TOPIC_REFUNDED)
                    .payload(payload)
                    .partitionKey(payment.getOrderReference())
                    .status(PaymentOutboxEvent.OutboxStatus.PENDING)
                    .build();
            outboxRepository.save(outbox);
        } catch (JsonProcessingException ex) {
            // Serialisation of our own record cannot realistically fail; if it does, the
            // whole refund TX must roll back rather than leave a REFUNDED payment with no event.
            throw new IllegalStateException(
                    "Failed to serialise PaymentRefundedEvent for order " + payment.getOrderReference(), ex);
        }
    }
}
