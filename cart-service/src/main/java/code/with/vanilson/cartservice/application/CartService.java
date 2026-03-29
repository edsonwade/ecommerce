package code.with.vanilson.cartservice.application;

import code.with.vanilson.cartservice.domain.Cart;
import code.with.vanilson.cartservice.domain.CartItem;
import code.with.vanilson.cartservice.exception.CartNotFoundException;
import code.with.vanilson.cartservice.exception.CartValidationException;
import code.with.vanilson.cartservice.infrastructure.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * CartService — Application Layer
 * <p>
 * Core business logic for cart management.
 * <p>
 * Design decisions:
 * 1. Cart ID is deterministic: "cart:{customerId}" — no random UUID.
 *    This allows O(1) lookup without secondary index for the common case.
 * 2. addItem: if product already in cart → update quantity (merge), not duplicate.
 * 3. Every mutation calls cart.touch() → resets TTL in Redis (24h sliding window).
 * 4. checkout() → returns the cart snapshot for OrderService, then clears the cart.
 *    Clearing is done AFTER the order is created (caller responsibility) to prevent
 *    cart loss if order creation fails.
 * 5. All messages from messages.properties via MessageSource (SOLID-S).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartMapper     cartMapper;
    private final MessageSource  messageSource;

    public CartService(CartRepository cartRepository,
                       CartMapper cartMapper,
                       MessageSource messageSource) {
        this.cartRepository = cartRepository;
        this.cartMapper     = cartMapper;
        this.messageSource  = messageSource;
    }

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    public CartResponse getCart(String customerId) {
        Cart cart = findOrThrow(buildCartId(customerId), customerId);
        log.info(msg("cart.log.fetched", cart.getCartId(), cart.getItemCount()));
        return cartMapper.toResponse(cart);
    }

    // -------------------------------------------------------
    // WRITE — add item
    // -------------------------------------------------------

    public CartResponse addItem(String customerId, AddCartItemRequest request) {
        if (request.quantity() <= 0) {
            throw new CartValidationException(
                    msg("cart.item.quantity.invalid", request.productId()),
                    "cart.item.quantity.invalid");
        }

        String cartId = buildCartId(customerId);
        Cart   cart   = cartRepository.findById(cartId).orElseGet(() -> {
            Cart newCart = Cart.builder()
                    .cartId(cartId)
                    .customerId(customerId)
                    .items(new ArrayList<>())
                    .build();
            log.info(msg("cart.log.created", cartId, customerId));
            return newCart;
        });

        // Merge: if product already in cart, increment quantity
        cart.findItem(request.productId()).ifPresentOrElse(
                existing -> existing.setQuantity(existing.getQuantity() + request.quantity()),
                () -> cart.getItems().add(CartItem.builder()
                        .productId(request.productId())
                        .productName(request.productName())
                        .productDescription(request.productDescription())
                        .unitPrice(request.unitPrice())
                        .quantity(request.quantity())
                        .build())
        );

        cart.touch();
        cartRepository.save(cart);
        log.info(msg("cart.log.item.added", cartId, request.productId(), request.quantity()));
        return cartMapper.toResponse(cart);
    }

    // -------------------------------------------------------
    // WRITE — update item quantity
    // -------------------------------------------------------

    public CartResponse updateItemQuantity(String customerId, Integer productId, double newQuantity) {
        if (newQuantity <= 0) {
            throw new CartValidationException(
                    msg("cart.item.quantity.invalid", productId),
                    "cart.item.quantity.invalid");
        }

        String cartId = buildCartId(customerId);
        Cart   cart   = findOrThrow(cartId, customerId);

        CartItem item = cart.findItem(productId).orElseThrow(() ->
                new CartNotFoundException(
                        msg("cart.item.not.found", productId, cartId),
                        "cart.item.not.found"));

        item.setQuantity(newQuantity);
        cart.touch();
        cartRepository.save(cart);
        log.info(msg("cart.log.item.updated", cartId, productId, newQuantity));
        return cartMapper.toResponse(cart);
    }

    // -------------------------------------------------------
    // WRITE — remove item
    // -------------------------------------------------------

    public CartResponse removeItem(String customerId, Integer productId) {
        String cartId = buildCartId(customerId);
        Cart   cart   = findOrThrow(cartId, customerId);

        boolean removed = cart.getItems()
                .removeIf(i -> i.getProductId().equals(productId));

        if (!removed) {
            throw new CartNotFoundException(
                    msg("cart.item.not.found", productId, cartId),
                    "cart.item.not.found");
        }

        cart.touch();
        cartRepository.save(cart);
        log.info(msg("cart.log.item.removed", cartId, productId));
        return cartMapper.toResponse(cart);
    }

    // -------------------------------------------------------
    // WRITE — clear cart
    // -------------------------------------------------------

    public void clearCart(String customerId) {
        String cartId = buildCartId(customerId);
        cartRepository.deleteById(cartId);
        log.info(msg("cart.log.cleared", cartId));
    }

    // -------------------------------------------------------
    // CHECKOUT snapshot — returns cart for order creation
    // NOTE: Cart is NOT cleared here. Caller (order flow) clears
    //       the cart AFTER the order is persisted successfully.
    //       This prevents cart loss on order creation failure.
    // -------------------------------------------------------

    public CartResponse checkoutSnapshot(String customerId) {
        String cartId = buildCartId(customerId);
        Cart   cart   = findOrThrow(cartId, customerId);

        if (cart.getItems().isEmpty()) {
            throw new CartValidationException(
                    msg("cart.checkout.empty"), "cart.checkout.empty");
        }

        log.info(msg("cart.log.checkout", cartId, customerId, cart.getItemCount()));
        return cartMapper.toResponse(cart);
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    private Cart findOrThrow(String cartId, String customerId) {
        return cartRepository.findById(cartId).orElseThrow(() ->
                new CartNotFoundException(
                        msg("cart.not.found", cartId),
                        "cart.not.found"));
    }

    /** Deterministic cart ID: "cart:{customerId}" — no random UUID. */
    private String buildCartId(String customerId) {
        return "cart:" + customerId;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
