package code.with.vanilson.paymentservice.infrastructure.kafka;

import java.time.Instant;

/**
 * PaymentFailedEvent — Kafka event published by payment-service on failure.
 * Topic: payment.failed
 * Consumed by: order-service (cancels) + product-service (releases stock)
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
