package code.with.vanilson.cartservice;

import code.with.vanilson.cartservice.application.AddCartItemRequest;
import code.with.vanilson.cartservice.application.CartMapper;
import code.with.vanilson.cartservice.application.CartResponse;
import code.with.vanilson.cartservice.application.CartService;
import code.with.vanilson.cartservice.domain.Cart;
import code.with.vanilson.cartservice.domain.CartItem;
import code.with.vanilson.cartservice.exception.CartNotFoundException;
import code.with.vanilson.cartservice.exception.CartValidationException;
import code.with.vanilson.cartservice.infrastructure.CartRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CartServiceTest — Unit Tests
 * <p>
 * Framework: JUnit 5 + Mockito + AssertJ.
 * &#064;Nested  classes group scenarios by operation.
 * MessageSource mocked to return the key — no .properties file dependency.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService — Unit Tests")
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartMapper     cartMapper;
    @Mock private MessageSource  messageSource;

    @InjectMocks
    private CartService cartService;

    private static final String TENANT_ID   = "11111111-1111-1111-1111-111111111111";
    private static final String CUSTOMER_ID = "cust-001";
    private static final String CART_ID     = "cart:" + TENANT_ID + ":" + CUSTOMER_ID;

    private Cart emptyCart;
    private Cart cartWithOneItem;
    private CartItem laptopItem;
    private AddCartItemRequest addLaptopRequest;

    @BeforeEach
    void setUp() {
        // Tenant-scoped cart keys: buildCartId requires a tenant on the thread
        code.with.vanilson.tenantcontext.TenantContext.setCurrentTenantId(TENANT_ID);

        // MessageSource stub — returns key so tests don't depend on .properties file
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        laptopItem = CartItem.builder()
                .productId(1)
                .productName("Laptop")
                .productDescription("Gaming Laptop")
                .unitPrice(BigDecimal.valueOf(1200.00))
                .quantity(2.0)
                .availableQuantity(10)
                .build();

        emptyCart = Cart.builder()
                .cartId(CART_ID)
                .customerId(CUSTOMER_ID)
                .items(new ArrayList<>())
                .build();

        cartWithOneItem = Cart.builder()
                .cartId(CART_ID)
                .customerId(CUSTOMER_ID)
                .items(new ArrayList<>(List.of(laptopItem)))
                .build();

        addLaptopRequest = new AddCartItemRequest(
                1, "Laptop", "Gaming Laptop",
                BigDecimal.valueOf(1200.00), 2.0, 10);
    }

    @AfterEach
    void tearDown() {
        code.with.vanilson.tenantcontext.TenantContext.clear();
    }

    // -------------------------------------------------------
    // getCart
    // -------------------------------------------------------
    @Nested @DisplayName("getCart")
    class GetCart {

        @Test @DisplayName("should return cart when found")
        void shouldReturnCartWhenFound() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartMapper.toResponse(cartWithOneItem)).thenReturn(expectedResponse);

            CartResponse result = cartService.getCart(CUSTOMER_ID);

            assertThat(result).isNotNull();
            assertThat(result.cartId()).isEqualTo(CART_ID);
            assertThat(result.itemCount()).isEqualTo(1);
        }

        @Test @DisplayName("should return an empty cart (200, not 404) when none exists yet")
        void shouldReturnEmptyCartWhenNotFound() {
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.empty());
            CartResponse emptyResponse = buildResponse(CART_ID, CUSTOMER_ID, 0);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(emptyResponse);

            CartResponse result = cartService.getCart(CUSTOMER_ID);

            assertThat(result).isNotNull();
            assertThat(result.itemCount()).isEqualTo(0);
            // Probing an empty cart must not persist anything to Redis.
            verify(cartRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------
    // addItem
    // -------------------------------------------------------
    @Nested @DisplayName("addItem")
    class AddItem {

        @Test @DisplayName("should create cart and add item when cart does not exist")
        void shouldCreateCartOnFirstItem() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(emptyCart);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            CartResponse result = cartService.addItem(CUSTOMER_ID, addLaptopRequest);

            assertThat(result).isNotNull();
            verify(cartRepository, times(1)).save(any(Cart.class));
        }

        @Test @DisplayName("should add item to existing cart")
        void shouldAddItemToExistingCart() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(emptyCart));
            when(cartRepository.save(any(Cart.class))).thenReturn(emptyCart);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            cartService.addItem(CUSTOMER_ID, addLaptopRequest);

            // Verify save was called with cart containing new item
            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getItems()).hasSize(1);
            assertThat(captor.getValue().getItems().get(0).getProductId()).isEqualTo(1);
        }

        @Test @DisplayName("should merge quantity when same product added twice")
        void shouldMergeQuantityOnDuplicateProduct() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cartWithOneItem);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            // Add same product again with qty=1
            AddCartItemRequest duplicateRequest = new AddCartItemRequest(
                    1, "Laptop", "Gaming Laptop", BigDecimal.valueOf(1200.00), 1.0, 10);
            cartService.addItem(CUSTOMER_ID, duplicateRequest);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            // Original qty=2 + new qty=1 = 3
            assertThat(captor.getValue().getItems().get(0).getQuantity()).isEqualTo(3.0);
            // Only one line item (merged, not duplicated)
            assertThat(captor.getValue().getItems()).hasSize(1);
        }

        @Test @DisplayName("should throw CartValidationException when quantity is zero")
        void shouldThrowWhenQuantityZero() {
            AddCartItemRequest zeroQtyRequest = new AddCartItemRequest(
                    1, "Laptop", "desc", BigDecimal.valueOf(100), 0.0, 10);

            assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, zeroQtyRequest))
                    .isInstanceOf(CartValidationException.class)
                    .hasMessageContaining("cart.item.quantity.invalid");
            verify(cartRepository, never()).save(any());
        }

        @Test @DisplayName("should throw CartValidationException when quantity is negative")
        void shouldThrowWhenQuantityNegative() {
            AddCartItemRequest negQtyRequest = new AddCartItemRequest(
                    1, "Laptop", "desc", BigDecimal.valueOf(100), -1.0, 10);

            assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, negQtyRequest))
                    .isInstanceOf(CartValidationException.class);
        }
    }

    // -------------------------------------------------------
    // addItem — Idempotency-Key (B4)
    // -------------------------------------------------------
    @Nested @DisplayName("addItem — Idempotency-Key (B4)")
    class AddItemIdempotency {

        private static final String KEY = "idem-key-001";

        @Test @DisplayName("should record the key and apply the add on first delivery")
        void shouldApplyAndRecordKeyOnFirstDelivery() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cartWithOneItem);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            cartService.addItem(CUSTOMER_ID, addLaptopRequest, KEY);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            // The add applied (2 existing + 2 requested = 4) and the key was recorded
            assertThat(captor.getValue().getItems().get(0).getQuantity()).isEqualTo(4.0);
            assertThat(captor.getValue().hasIdempotencyKey(KEY)).isTrue();
        }

        @Test @DisplayName("should NOT increment quantity when the same key is replayed")
        void shouldNotIncrementOnReplayedKey() {
            // Cart state after the first (successful) add: key already applied
            cartWithOneItem.recordIdempotencyKey(KEY);
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            CartResponse result = cartService.addItem(CUSTOMER_ID, addLaptopRequest, KEY);

            assertThat(result).isNotNull();
            // Replay must not mutate or persist anything
            verify(cartRepository, never()).save(any());
            assertThat(cartWithOneItem.getItems().get(0).getQuantity()).isEqualTo(2.0);
        }

        @Test @DisplayName("should apply adds with different keys separately")
        void shouldApplyDifferentKeysSeparately() {
            cartWithOneItem.recordIdempotencyKey(KEY);
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cartWithOneItem);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            cartService.addItem(CUSTOMER_ID, addLaptopRequest, "idem-key-002");

            // A different key is a different user action — the merge applies
            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getItems().get(0).getQuantity()).isEqualTo(4.0);
            assertThat(captor.getValue().hasIdempotencyKey("idem-key-002")).isTrue();
        }

        @Test @DisplayName("should keep legacy merge behaviour when no key is sent")
        void shouldKeepLegacyBehaviourWithoutKey() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cartWithOneItem);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            cartService.addItem(CUSTOMER_ID, addLaptopRequest, null);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getItems().get(0).getQuantity()).isEqualTo(4.0);
            // No key sent → nothing recorded
            assertThat(captor.getValue().getAppliedIdempotencyKeys()).isEmpty();
        }

        @Test @DisplayName("should treat a blank key as absent")
        void shouldTreatBlankKeyAsAbsent() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cartWithOneItem);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            cartService.addItem(CUSTOMER_ID, addLaptopRequest, "   ");

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getAppliedIdempotencyKeys()).isEmpty();
        }
    }

    // -------------------------------------------------------
    // updateItemQuantity
    // -------------------------------------------------------
    @Nested @DisplayName("updateItemQuantity")
    class UpdateItemQty {

        @Test @DisplayName("should update quantity of existing item")
        void shouldUpdateQuantitySuccessfully() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cartWithOneItem);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            cartService.updateItemQuantity(CUSTOMER_ID, 1, 5.0);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getItems().get(0).getQuantity()).isEqualTo(5.0);
        }

        @Test @DisplayName("should throw CartNotFoundException when item not in cart")
        void shouldThrowWhenItemNotInCart() {
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(emptyCart));

            assertThatThrownBy(() -> cartService.updateItemQuantity(CUSTOMER_ID, 99, 2.0))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining("cart.item.not.found");
        }

        @Test @DisplayName("should throw CartValidationException when new quantity is zero")
        void shouldThrowWhenNewQuantityZero() {
            assertThatThrownBy(() -> cartService.updateItemQuantity(CUSTOMER_ID, 1, 0.0))
                    .isInstanceOf(CartValidationException.class);
        }

        @Test @DisplayName("should throw CartValidationException when new quantity exceeds stock")
        void shouldThrowWhenNewQuantityExceedsStock() {
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            // laptopItem has availableQuantity=10, requesting qty=11
            assertThatThrownBy(() -> cartService.updateItemQuantity(CUSTOMER_ID, 1, 11.0))
                    .isInstanceOf(CartValidationException.class)
                    .hasMessageContaining("cart.item.exceeds.stock");
            verify(cartRepository, never()).save(any());
        }

        @Test @DisplayName("should allow update when new quantity equals stock limit")
        void shouldAllowUpdateAtStockLimit() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cartWithOneItem);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            // Exactly at the limit (10) should succeed
            cartService.updateItemQuantity(CUSTOMER_ID, 1, 10.0);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getItems().get(0).getQuantity()).isEqualTo(10.0);
        }
    }

    // -------------------------------------------------------
    // removeItem
    // -------------------------------------------------------
    @Nested @DisplayName("removeItem")
    class RemoveItem {

        @Test @DisplayName("should remove item from cart")
        void shouldRemoveItemSuccessfully() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 0);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cartWithOneItem);
            when(cartMapper.toResponse(any(Cart.class))).thenReturn(expectedResponse);

            cartService.removeItem(CUSTOMER_ID, 1);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getItems()).isEmpty();
        }

        @Test @DisplayName("should throw CartNotFoundException when item not in cart")
        void shouldThrowWhenItemNotInCart() {
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(emptyCart));

            assertThatThrownBy(() -> cartService.removeItem(CUSTOMER_ID, 99))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining("cart.item.not.found");
            verify(cartRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------
    // clearCart
    // -------------------------------------------------------
    @Nested @DisplayName("clearCart")
    class ClearCart {

        @Test @DisplayName("should delete cart from Redis")
        void shouldDeleteCart() {
            cartService.clearCart(CUSTOMER_ID);
            verify(cartRepository, times(1)).deleteById(CART_ID);
        }
    }

    // -------------------------------------------------------
    // checkoutSnapshot
    // -------------------------------------------------------
    @Nested @DisplayName("checkoutSnapshot")
    class CheckoutSnapshot {

        @Test @DisplayName("should return cart snapshot without clearing it")
        void shouldReturnSnapshotWithoutClearing() {
            CartResponse expectedResponse = buildResponse(CART_ID, CUSTOMER_ID, 1);
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartWithOneItem));
            when(cartMapper.toResponse(cartWithOneItem)).thenReturn(expectedResponse);

            CartResponse result = cartService.checkoutSnapshot(CUSTOMER_ID);

            assertThat(result).isNotNull();
            assertThat(result.itemCount()).isEqualTo(1);
            // Cart must NOT be deleted during checkout snapshot
            verify(cartRepository, never()).deleteById(any());
        }

        @Test @DisplayName("should throw CartValidationException when cart is empty")
        void shouldThrowWhenCartEmpty() {
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(emptyCart));

            assertThatThrownBy(() -> cartService.checkoutSnapshot(CUSTOMER_ID))
                    .isInstanceOf(CartValidationException.class)
                    .hasMessageContaining("cart.checkout.empty");
        }

        @Test @DisplayName("should throw CartNotFoundException when no cart exists")
        void shouldThrowWhenCartNotFound() {
            when(cartRepository.findById(CART_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.checkoutSnapshot(CUSTOMER_ID))
                    .isInstanceOf(CartNotFoundException.class);
        }
    }

    // -------------------------------------------------------
    // helpers
    // -------------------------------------------------------
    private CartResponse buildResponse(String cartId, String customerId, int itemCount) {
        return new CartResponse(cartId, customerId, List.of(),
                BigDecimal.ZERO, itemCount, null, null);
    }
}
