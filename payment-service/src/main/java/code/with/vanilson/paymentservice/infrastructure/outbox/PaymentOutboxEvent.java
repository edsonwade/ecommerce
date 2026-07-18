package code.with.vanilson.paymentservice.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * PaymentOutboxEvent — Infrastructure Entity (Transactional Outbox, Fase 6.1).
 * <p>
 * Localised clone of order-service's {@code OutboxEvent}. Written in the SAME
 * transaction as the payment status change so the HTTP refund request never
 * blocks on a Kafka send — {@link PaymentOutboxPublisher} drains PENDING rows to
 * the broker off the request thread. See migration {@code V1.5}.
 * <p>
 * The Transactional Outbox Pattern solves the dual-write problem: "How do I
 * atomically write to the DB AND publish to Kafka?" — write both to the DB in one
 * TX, then publish the row asynchronously and mark it PUBLISHED.
 * <p>
 * Idempotency: consumers dedupe via {@code eventId}. No Hibernate {@code @Filter}
 * — the publisher scheduler needs cross-tenant visibility to publish every
 * tenant's events (mirrors order-service).
 * <p>
 * <b>Follow-up (registered):</b> this duplicates order-service's outbox. A shared
 * {@code outbox} starter should later replace both copies (see the F6.1 plan).
 *
 * @author vamuhong
 * @version 1.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "payment_outbox_event")
public class PaymentOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** SaaS tenant UUID — audit trail for which tenant triggered this event. */
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    /** UUID matching the Kafka event's eventId — used for consumer idempotency. */
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    /** Correlation ID (the order reference) tying the event to its saga. */
    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    /** Kafka topic to publish to. */
    @Column(nullable = false, length = 100)
    private String topic;

    /** Serialised JSON payload of the event. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Partition key for Kafka (order reference — same-order events stay ordered). */
    @Column(name = "partition_key", length = 36)
    private String partitionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }
}
