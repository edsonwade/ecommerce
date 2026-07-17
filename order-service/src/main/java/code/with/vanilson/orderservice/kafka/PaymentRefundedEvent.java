package code.with.vanilson.orderservice.kafka;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaymentRefundedEvent — Kafka Event (Fase 6, consumer-side copy).
 * <p>
 * Consumed by order-service from topic: payment.refunded (published by payment-service
 * after {@code PaymentService.refundPayment}). Field names/order mirror the producer-side
 * record exactly — {@code StringJsonMessageConverter} binds by JSON field name, and this
 * POJO carries no {@code tenantId} or {@code correlationId} (payment-service doesn't store
 * either); order-service resolves both from the {@code Order} row found by {@code orderId}.
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
