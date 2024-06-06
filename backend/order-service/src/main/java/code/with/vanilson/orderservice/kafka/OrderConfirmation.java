package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.customerservice.CustomerResponse;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.productservice.purchase.PurchaseResponse;

import java.math.BigDecimal;
import java.util.List;

public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        CustomerResponse customer,
        List<PurchaseResponse> products

) {
}