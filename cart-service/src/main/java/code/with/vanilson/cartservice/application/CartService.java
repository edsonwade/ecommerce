package code.with.vanilson.cartservice.application;

import code.with.vanilson.cartservice.domain.Cart;
import code.with.vanilson.cartservice.domain.CartItem;
import code.with.vanilson.cartservice.exception.CartNotFoundException;
import code.with.vanilson.cartservice.exception.CartValidationException;
import code.with.vanilson.cartservice.infrastructure.CartRepository;
import code.with.vanilson.tenantcontext.TenantContext;
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
 * 1. Cart ID is deterministic and tenant-scoped: "cart:{tenantId}:{customerId}" — no random UUID.
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

    /**
     * Returns the customer's cart, or an empty (un-persisted) cart when none exists yet.
     * <p>
     * "The current user's cart" is a resource that conceptually always exists — a customer
     * who has never added an item simply has an empty one. Returning 200 + empty cart instead
     * of 404 stops the UI from showing a scary "cart.not.found" error on pages that probe the
     * cart (e.g. right after login/registration) and removes the matching WARN log noise.
     * The empty cart is NOT saved to Redis here — we only create a row when an item is added.
     */
    public CartResponse getCart(String customerId) {
        String cartId = buildCartId(customerId);
        Cart cart = cartRepository.findById(cartId).orElseGet(() -> Cart.builder()
                .cartId(cartId)
                .customerId(customerId)
                .tenantId(TenantContext.requireCurrentTenantId())
                .items(new ArrayList<>())
                .build());
        log.info(msg("cart.log.fetched", cart.getCartId(), cart.getItemCount()));
        return cartMapper.toResponse(cart);
    }

    // -------------------------------------------------------
    // WRITE — add item
    // -------------------------------------------------------

    /** Legacy entry point without idempotency — kept for callers that send no key. */
    public CartResponse addItem(String customerId, AddCartItemRequest request) {
        return addItem(customerId, request, null);
    }

    /**
     * Adds an item to the cart, idempotently when an {@code Idempotency-Key} is supplied.
     * <p>
     * B4: addItem is a RELATIVE mutation (it increments the quantity), so a client
     * retry of the same request — e.g. after a false 503 from a stale gateway
     * keep-alive on a write that actually succeeded, the same failure mode that
     * duplicated orders — used to double the quantity. When the client sends an
     * {@code Idempotency-Key}, keys already applied to this cart are answered with
     * the current cart unchanged. The applied keys live on the cart hash itself
     * (bounded FIFO), so the replay window expires with the cart's TTL / checkout
     * clear — acceptable, because a cleared cart ends the shopping session the key
     * belonged to. Absolute mutations (update quantity, remove, clear) are already
     * replay-safe and take no key.
     *
     * @param idempotencyKey client-generated key, stable across retries of one add click; may be null
     */
    public CartResponse addItem(String customerId, AddCartItemRequest request, String idempotencyKey) {
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
                    .tenantId(TenantContext.requireCurrentTenantId())
                    .items(new ArrayList<>())
                    .build();
            log.info(msg("cart.log.created", cartId, customerId));
            return newCart;
        });

        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();

        // Idempotent replay: this exact add was already applied — return the cart
        // as it stands instead of incrementing the quantity a second time.
        if (hasIdempotencyKey && cart.hasIdempotencyKey(idempotencyKey)) {
            log.info(msg("cart.log.item.replayed", cartId, request.productId(), idempotencyKey));
            return cartMapper.toResponse(cart);
        }

        // Merge: if product already in cart, increment quantity
        cart.findItem(request.productId()).ifPresentOrElse(
                existing -> existing.setQuantity(existing.getQuantity() + request.quantity()),
                () -> cart.getItems().add(CartItem.builder()
                        .productId(request.productId())
                        .productName(request.productName())
                        .productDescription(request.productDescription())
                        .unitPrice(request.unitPrice())
                        .quantity(request.quantity())
                        .availableQuantity(request.availableQuantity())
                        .build())
        );

        if (hasIdempotencyKey) {
            cart.recordIdempotencyKey(idempotencyKey);
        }

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

        if (item.getAvailableQuantity() != null && newQuantity > item.getAvailableQuantity()) {
            throw new CartValidationException(
                    msg("cart.item.exceeds.stock", productId, item.getAvailableQuantity()),
                    "cart.item.exceeds.stock");
        }

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

    /**
     * Deterministic tenant-scoped cart ID: "cart:{tenantId}:{customerId}" — no random UUID.
     * Without the tenant segment, customer "user1" in Tenant A and "user1" in
     * Tenant B would share the same Redis key and see each other's items.
     * requireCurrentTenantId() fails fast instead of silently building "cart:null:…" keys.
     */
    private String buildCartId(String customerId) {
        return "cart:" + TenantContext.requireCurrentTenantId() + ":" + customerId;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
