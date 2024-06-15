package code.with.vanilson.notification.kafka.order;

import code.with.vanilson.customerservice.Customer;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.productservice.Product;

import java.math.BigDecimal;
import java.util.List;

public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        Customer customer,
        List<Product> products

) {
}