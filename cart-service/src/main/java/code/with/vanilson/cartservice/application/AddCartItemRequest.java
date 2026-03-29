package code.with.vanilson.cartservice.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * AddCartItemRequest — Application Layer DTO
 * Payload for POST /api/v1/carts/{customerId}/items
 *
 * @author vamuhong
 * @version 2.0
 */
public record AddCartItemRequest(

        @NotNull(message = "{cart.product.id.required}")
        Integer productId,

        @NotBlank(message = "{cart.product.id.required}")
        String productName,

        String productDescription,

        @NotNull(message = "{cart.item.quantity.invalid}")
        BigDecimal unitPrice,

        @Positive(message = "{cart.item.quantity.invalid}")
        double quantity
) {}
