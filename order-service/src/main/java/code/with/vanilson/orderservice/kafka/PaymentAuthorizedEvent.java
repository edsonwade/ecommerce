package code.with.vanilson.orderservice.kafka;

import java.time.Instant;

/**
 * PaymentAuthorizedEvent — Kafka Event (Phase 3)
 * <p>
 * Consumed by order-service from topic: payment.authorized
 * Published by payment-service after successful payment processing.
 * Triggers final order CONFIRMED state.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
public record PaymentAuthorizedEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        String  paymentId,
        Instant occurredAt,
        int     schemaVersion
) {}
