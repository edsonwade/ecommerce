package code.with.vanilson.productservice.kafka;

import java.time.Instant;

/**
 * OrderRefundedEvent — local DTO consumed from topic: order.refunded
 * Published via order-service's outbox after a payment refund is applied. Used by
 * {@link RefundRestockConsumer} to restore reserved stock for the refunded order.
 * Field names mirror order-service's producer-side record exactly — bound by
 * {@code StringJsonMessageConverter} via the listener's declared payload type.
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
) {}
