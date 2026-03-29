package code.with.vanilson.cartservice.application;

import code.with.vanilson.cartservice.domain.Cart;
import code.with.vanilson.cartservice.domain.CartItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CartMapper — Application Layer
 * Single Responsibility (SOLID-S): only Cart → CartResponse mapping.
 *
 * @author vamuhong
 * @version 2.0
 */
@Component
public class CartMapper {

    public CartResponse toResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new CartResponse(
                cart.getCartId(),
                cart.getCustomerId(),
                itemResponses,
                cart.getTotal(),
                cart.getItemCount(),
                cart.getCreatedAt(),
                cart.getUpdatedAt()
        );
    }

    private CartResponse.CartItemResponse toItemResponse(CartItem item) {
        return new CartResponse.CartItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getProductDescription(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
