package code.with.vanilson.paymentservice.infrastructure.kafka;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaymentRefundedEvent — Kafka event published by payment-service on a successful refund.
 * Topic: payment.refunded
 * Consumed by: order-service (transitions the order to REFUNDED via the payment saga extension)
 *
 * @author vamuhong
 * @version 1.0
 */
public record PaymentRefundedEvent(
        String  eventId,
        Integer paymentId,
        Integer orderId,
        String  orderReference,
        BigDecimal amount,
        Instant occurredAt,
        int     version
) {
}
