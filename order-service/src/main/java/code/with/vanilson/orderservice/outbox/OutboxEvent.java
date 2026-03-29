package code.with.vanilson.orderservice.outbox;

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
 * OutboxEvent — Infrastructure Entity (Outbox Pattern)
 * <p>
 * The Transactional Outbox Pattern solves the dual-write problem:
 * "How do I atomically write to the DB AND publish to Kafka?"
 * <p>
 * Problem with naive approach:
 *   1. Save order to DB ✓
 *   2. Publish to Kafka ✗ (Kafka down) → order saved but event never published → inconsistency
 * <p>
 * Outbox solution:
 *   1. Save order + OutboxEvent in the SAME DB transaction (atomic)
 *   2. OutboxEventPublisher reads unpublished events and publishes to Kafka
 *   3. Mark event as PUBLISHED after successful Kafka send
 *   → If Kafka is down: order is saved, event stays in outbox → retried when Kafka recovers
 *   → If app crashes after DB write: event is retried on restart (at-least-once delivery)
 * <p>
 * Idempotency: consumers must handle duplicates via eventId check.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** UUID matching the Kafka event's eventId — used for consumer idempotency. */
    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    /** Correlation ID of the order saga. */
    @Column(nullable = false, length = 36)
    private String correlationId;

    /** Kafka topic to publish to. */
    @Column(nullable = false, length = 100)
    private String topic;

    /** Serialised JSON payload of the event. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Partition key for Kafka (correlationId for saga ordering). */
    @Column(length = 36)
    private String partitionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt  = LocalDateTime.now();

    private LocalDateTime publishedAt;

    /** Retry count — stops retrying after 5 attempts. */
    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    public boolean canRetry() {
        return retryCount < 5 && status == OutboxStatus.PENDING;
    }

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }
}
