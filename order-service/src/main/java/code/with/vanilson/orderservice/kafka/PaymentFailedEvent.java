package code.with.vanilson.orderservice.kafka;

import java.time.Instant;

/**
 * PaymentFailedEvent — Kafka Event (Phase 3)
 * <p>
 * Consumed by order-service from topic: payment.failed
 * Triggers CANCELLED state + inventory release.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
public record PaymentFailedEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        String  reason,
        Instant occurredAt,
        int     schemaVersion
) {}
