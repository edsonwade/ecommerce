package code.with.vanilson.orderservice.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * ProductPurchaseRequest
 * <p>
 * Local DTO owned exclusively by order-service.
 * Represents a product purchase request sent to product-service via HTTP.
 * No dependency on product-service JAR — contract is API-based only.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record ProductPurchaseRequest(
        @NotNull(message = "{order.validation.products.required}")
        Integer productId,

        @Positive(message = "{order.product.quantity.invalid}")
        double quantity
) {
}
