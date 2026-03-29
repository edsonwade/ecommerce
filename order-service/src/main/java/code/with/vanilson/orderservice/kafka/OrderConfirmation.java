package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.orderservice.customer.CustomerInfo;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.orderservice.product.ProductPurchaseResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * OrderConfirmation
 * <p>
 * Kafka event payload published to 'order-topic' after successful order creation.
 * Consumed by notification-service to send confirmation emails.
 * Uses local DTOs only — no cross-service class imports.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        CustomerInfo customer,
        List<ProductPurchaseResponse> products
) {
}
