package code.with.vanilson.paymentservice;

import code.with.vanilson.customerservice.Customer;
import code.with.vanilson.orderservice.payment.PaymentMethod;

import java.math.BigDecimal;

public record PaymentRequest(
        Integer id,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        Integer orderId,
        String orderReference,
        Customer customer
) {
}