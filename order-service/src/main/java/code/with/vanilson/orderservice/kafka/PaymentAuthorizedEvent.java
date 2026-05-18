package code.with.vanilson.orderservice.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaymentAuthorizedEvent — Kafka Event (Phase 4)
 * <p>
 * Consumed by order-service from topic: payment.authorized
 * Published by payment-service after successful payment processing.
 * Triggers final order CONFIRMED state and order confirmation notification.
 * </p>
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
