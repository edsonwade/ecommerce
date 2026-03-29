package code.with.vanilson.orderservice.product;

import java.math.BigDecimal;

/**
 * ProductPurchaseResponse
 * <p>
 * Local DTO owned exclusively by order-service.
 * Represents the product data returned by product-service after a successful purchase.
 * No dependency on product-service JAR — contract is API-based only.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record ProductPurchaseResponse(
        Integer productId,
        String name,
        String description,
        BigDecimal price,
        double quantity
) {
}
