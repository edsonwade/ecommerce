package code.with.vanilson.paymentservice.infrastructure.kafka;

import java.time.Instant;

/**
 * PaymentAuthorizedEvent — Kafka event published by payment-service on success.
 * Topic: payment.authorized
 * Consumed by: order-service (confirms the order)
 *
 * @author vamuhong
 * @version 3.0
 */
public record PaymentAuthorizedEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        Integer paymentId,
        Instant occurredAt,
        int     schemaVersion
) {}
