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
 * Phase 4: tenantId added for audit trail — which tenant triggered this event.
 * No Hibernate @Filter: the OutboxEventPublisher scheduler needs cross-tenant
 * visibility to publish events from all tenants.
 * <p>
 * The Transactional Outbox Pattern solves the dual-write problem:
 * "How do I atomically write to the DB AND publish to Kafka?"
 * <p>
 * Outbox solution:
 *   1. Save order + OutboxEvent in the SAME DB transaction (atomic)
 *   2. OutboxEventPublisher reads unpublished events and publishes to Kafka
 *   3. Mark event as PUBLISHED after successful Kafka send
 * <p>
 * Idempotency: consumers must handle duplicates via eventId check.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
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

    /** SaaS tenant UUID — audit trail for which tenant triggered this event. */
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

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
