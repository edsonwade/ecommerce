package code.with.vanilson.paymentservice.infrastructure.messaging;

import java.math.BigDecimal;

/**
 * PaymentNotificationRequest — Infrastructure Layer DTO
 * <p>
 * Kafka event payload published to 'payment-topic' after a successful payment.
 * Consumed by notification-service to send payment confirmation email.
 * <p>
 * Owned exclusively by payment-service — no cross-service class imports.
 * Uses primitive/value types only for safe serialisation across service boundaries.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record PaymentNotificationRequest(
        String orderReference,
        BigDecimal amount,
        String paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail
) {
}
