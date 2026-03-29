package code.with.vanilson.orderservice.payment;

import code.with.vanilson.orderservice.customer.CustomerInfo;

import java.math.BigDecimal;

/**
 * PaymentRequest
 * <p>
 * Local DTO owned exclusively by order-service.
 * Payload sent to payment-service via HTTP.
 * CustomerInfo is order-service's local record — no shared JAR.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record PaymentRequest(
        BigDecimal amount,
        PaymentMethod paymentMethod,
        Integer orderId,
        String orderReference,
        CustomerInfo customer
) {
}
