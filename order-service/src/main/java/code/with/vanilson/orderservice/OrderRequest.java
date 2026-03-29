package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.orderservice.product.ProductPurchaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * OrderRequest
 * <p>
 * Validated request record for creating an order.
 * All validation messages reference messages.properties keys via {key} syntax.
 * Uses local PaymentMethod and ProductPurchaseRequest — no cross-service imports.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record OrderRequest(
        Integer id,

        String reference,

        @Positive(message = "{order.validation.amount.positive}")
        @NotNull(message = "{order.validation.amount.positive}")
        BigDecimal amount,

        @NotNull(message = "{order.validation.payment.method.required}")
        PaymentMethod paymentMethod,

        @NotNull(message = "{order.validation.customer.required}")
        @NotBlank(message = "{order.validation.customer.required}")
        String customerId,

        @NotNull(message = "{order.validation.products.required}")
        @NotEmpty(message = "{order.validation.products.required}")
        @Valid
        List<ProductPurchaseRequest> products
) {
}
