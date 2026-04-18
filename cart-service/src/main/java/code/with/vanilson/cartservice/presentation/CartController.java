package code.with.vanilson.cartservice.presentation;

import code.with.vanilson.cartservice.application.AddCartItemRequest;
import code.with.vanilson.cartservice.application.CartResponse;
import code.with.vanilson.cartservice.application.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CartController — Presentation Layer
 * <p>
 * REST endpoints for cart management.
 * Single Responsibility (SOLID-S): HTTP concerns only — all logic in CartService.
 * <p>
 * Endpoints:
 * GET    /api/v1/carts/{customerId}                    → get cart
 * POST   /api/v1/carts/{customerId}/items              → add item (creates cart if absent)
 * PATCH  /api/v1/carts/{customerId}/items/{productId}  → update item quantity
 * DELETE /api/v1/carts/{customerId}/items/{productId}  → remove item
 * DELETE /api/v1/carts/{customerId}                    → clear entire cart
 * GET    /api/v1/carts/{customerId}/checkout           → checkout snapshot (read-only)
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@Tag(name = "Cart API", description = "Shopping cart management — Redis-native, 24h session TTL")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get cart by customer ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cart retrieved"),
        @ApiResponse(responseCode = "404", description = "Cart not found")
    })
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.userId.toString()")
    @GetMapping("/{customerId}")
    public ResponseEntity<CartResponse> getCart(
            @PathVariable @Parameter(description = "Customer ID") String customerId) {
        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @Operation(summary = "Add item to cart",
               description = "Creates the cart if it doesn't exist. If product already in cart, quantity is added.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Item added to cart"),
        @ApiResponse(responseCode = "400", description = "Invalid item data")
    })
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.userId.toString()")
    @PostMapping("/{customerId}/items")
    public ResponseEntity<CartResponse> addItem(
            @PathVariable String customerId,
            @RequestBody @Valid AddCartItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartService.addItem(customerId, request));
    }

    @Operation(summary = "Update item quantity",
               description = "Sets the quantity of an existing cart item. Use 0 to effectively remove.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quantity updated"),
        @ApiResponse(responseCode = "404", description = "Cart or item not found"),
        @ApiResponse(responseCode = "400", description = "Invalid quantity")
    })
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.userId.toString()")
    @PatchMapping("/{customerId}/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable String customerId,
            @PathVariable Integer productId,
            @RequestParam @Positive(message = "{cart.item.quantity.invalid}") double quantity) {
        return ResponseEntity.ok(cartService.updateItemQuantity(customerId, productId, quantity));
    }

    @Operation(summary = "Remove item from cart")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item removed"),
        @ApiResponse(responseCode = "404", description = "Cart or item not found")
    })
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.userId.toString()")
    @DeleteMapping("/{customerId}/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable String customerId,
            @PathVariable Integer productId) {
        return ResponseEntity.ok(cartService.removeItem(customerId, productId));
    }

    @Operation(summary = "Clear entire cart",
               description = "Removes the cart from Redis entirely. Called by order-service after successful checkout.")
    @ApiResponse(responseCode = "204", description = "Cart cleared")
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.userId.toString()")
    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> clearCart(@PathVariable String customerId) {
        cartService.clearCart(customerId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Checkout snapshot",
               description = "Returns current cart contents for order creation. Cart is NOT cleared — " +
                             "call DELETE /{customerId} after order is persisted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot returned"),
        @ApiResponse(responseCode = "400", description = "Cart is empty"),
        @ApiResponse(responseCode = "404", description = "Cart not found")
    })
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.userId.toString()")
    @GetMapping("/{customerId}/checkout")
    public ResponseEntity<CartResponse> checkoutSnapshot(@PathVariable String customerId) {
        return ResponseEntity.ok(cartService.checkoutSnapshot(customerId));
    }
}
