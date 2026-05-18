package code.with.vanilson.paymentservice.infrastructure.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaymentAuthorizedEvent — Kafka event published by payment-service on success.
 * Topic: payment.authorized
 * Consumed by: order-service (confirms the order and sends notification)
 *
 * @author vamuhong
 * @version 4.0
 */
public record PaymentAuthorizedEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        Integer paymentId,
        String  customerId,
        String  customerEmail,
        String  customerFirstname,
        String  customerLastname,
        List<ReservedItem> reservedItems,
        BigDecimal totalAmount,
        String  paymentMethod,
        Instant occurredAt,
        int     schemaVersion
) {
    public record ReservedItem(Integer productId, String productName,
                               double quantity, BigDecimal unitPrice) {}
}
