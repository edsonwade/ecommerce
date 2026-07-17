package code.with.vanilson.orderservice.kafka;

import java.time.Instant;

/**
 * OrderRefundedEvent — Kafka event published via the order-service outbox after a
 * payment refund is applied.
 * Topic: order.refunded
 * Consumed by: product-service (restocks reserved quantities — mirrors the compensation
 * path used for cancelled orders).
 *
 * @author vamuhong
 * @version 1.0
 */
public record OrderRefundedEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        Instant occurredAt,
        int     version
) {
}
