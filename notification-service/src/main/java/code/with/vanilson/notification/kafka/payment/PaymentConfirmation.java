package code.with.vanilson.notification.kafka.payment;

import java.math.BigDecimal;

/**
 * PaymentConfirmation — Local Kafka event DTO (notification-service)
 * <p>
 * Represents the payment confirmation payload consumed from 'payment-topic'.
 * Owned exclusively by notification-service — no import from payment-service JAR.
 * Field names must match the JSON serialised by payment-service's PaymentNotificationRequest record.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record PaymentConfirmation(
        String orderReference,
        BigDecimal amount,
        String paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail
) {}
