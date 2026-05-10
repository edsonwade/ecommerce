package code.with.vanilson.cartservice.bdd;

import code.with.vanilson.cartservice.application.AddCartItemRequest;
import code.with.vanilson.cartservice.application.CartMapper;
import code.with.vanilson.cartservice.application.CartResponse;
import code.with.vanilson.cartservice.application.CartService;
import code.with.vanilson.cartservice.domain.Cart;
import code.with.vanilson.cartservice.domain.CartItem;
import code.with.vanilson.cartservice.exception.CartNotFoundException;
import code.with.vanilson.cartservice.exception.CartValidationException;
import code.with.vanilson.cartservice.infrastructure.CartRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class CartStepDefinitions {

    private CartService cartService;
    private CartRepository cartRepository;
    private CartMapper cartMapper;

    private String customerId;
    private CartResponse resultCart;
    private Exception caughtException;
    private Cart mockedDbCart;

    @Before
    public void setUp() {
        cartRepository = Mockito.mock(CartRepository.class);
        cartMapper = Mockito.mock(CartMapper.class);
        MessageSource messageSource = Mockito.mock(MessageSource.class);

        // Mock messageSource to return the key or a dummy string to avoid NPE
        when(messageSource.getMessage(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        cartService = new CartService(cartRepository, cartMapper, messageSource);
        caughtException = null;
    }

    @Given("the cart for customer {string} is empty")
    public void the_cart_is_empty(String custId) {
        this.customerId = custId;
        when(cartRepository.findById("cart:" + custId)).thenReturn(Optional.empty());
        mockedDbCart = Cart.builder().cartId("cart:" + custId).customerId(custId).items(new ArrayList<>()).build();
        when(cartRepository.save(any(Cart.class))).thenReturn(mockedDbCart);
    }

    @Given("the cart for customer {string} has product {int} with quantity {double}")
    public void the_cart_has_product_with_quantity(String custId, int productId, double quantity) {
        this.customerId = custId;
        CartItem item = CartItem.builder().productId(productId).quantity(quantity).unitPrice(BigDecimal.TEN).build();
        mockedDbCart = Cart.builder().cartId("cart:" + custId).customerId(custId)
                .items(new ArrayList<>(List.of(item))).build();

        when(cartRepository.findById("cart:" + custId)).thenReturn(Optional.of(mockedDbCart));
        when(cartRepository.save(any(Cart.class))).thenReturn(mockedDbCart);
    }

    @When("I add product {int} with quantity {double} to the cart")
    public void i_add_product_to_cart(int productId, double quantity) {
        AddCartItemRequest req = new AddCartItemRequest(
                productId, "TestProd", "Desc", BigDecimal.TEN, quantity, 20);

        // Define mapper behavior dynamically based on what was saved
        when(cartMapper.toResponse(any(Cart.class))).thenAnswer(inv -> {
            Cart saved = inv.getArgument(0);
            List<CartResponse.CartItemResponse> responses = saved.getItems().stream()
                    .map(i -> new CartResponse.CartItemResponse(i.getProductId(), i.getProductName(),
                            i.getProductDescription(), i.getUnitPrice(), i.getQuantity(), BigDecimal.TEN,
                            i.getAvailableQuantity()))
                    .toList();
            return new CartResponse(saved.getCartId(), saved.getCustomerId(), responses, BigDecimal.TEN, saved.getItems().size(), null, null);
        });

        try {
            resultCart = cartService.addItem(customerId, req);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @When("I attempt to add product {int} with quantity {double} to the cart")
    public void i_attempt_to_add_product_with_quantity_to_cart(int productId, double quantity) {
        AddCartItemRequest req = new AddCartItemRequest(
                productId, "TestProd", "Desc", BigDecimal.TEN, quantity, 20);
        try {
            cartService.addItem(customerId, req);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @When("I remove product {int} from the cart")
    public void i_remove_product_from_cart(int productId) {
        when(cartMapper.toResponse(any(Cart.class))).thenAnswer(inv -> {
            Cart saved = inv.getArgument(0);
            return new CartResponse(saved.getCartId(), saved.getCustomerId(), List.of(), BigDecimal.ZERO, 0, null, null);
        });
        resultCart = cartService.removeItem(customerId, productId);
    }

    @When("I checkout the cart")
    public void i_checkout_the_cart() {
        try {
            cartService.checkoutSnapshot(customerId);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("the cart should contain {int} item with product ID {int}")
    public void the_cart_should_contain_item_with_product_ID(int itemCount, int productId) {
        assertThat(caughtException).isNull();
        assertThat(resultCart.items()).hasSize(itemCount);
        assertThat(resultCart.items().get(0).productId()).isEqualTo(productId);
    }

    @Then("the total quantity for product {int} should be {double}")
    public void the_total_quantity_for_product_should_be(int productId, double qty) {
        assertThat(resultCart.items().get(0).quantity()).isEqualTo(qty);
    }

    @Then("the cart should be empty")
    public void the_cart_should_be_empty() {
        assertThat(resultCart.items()).isEmpty();
    }

    @Then("the system should throw a validation error")
    public void the_system_should_throw_validation_error() {
        assertThat(caughtException).isNotNull();
        assertThat(caughtException).isInstanceOfAny(CartValidationException.class, CartNotFoundException.class);
    }

    @Given("the cart for customer {string} has product {int} with quantity {double} and stock limit {int}")
    public void the_cart_has_product_with_quantity_and_stock_limit(String custId, int productId, double quantity, int stockLimit) {
        this.customerId = custId;
        CartItem item = CartItem.builder()
                .productId(productId)
                .quantity(quantity)
                .unitPrice(BigDecimal.TEN)
                .availableQuantity(stockLimit)
                .build();
        mockedDbCart = Cart.builder().cartId("cart:" + custId).customerId(custId)
                .items(new ArrayList<>(List.of(item))).build();

        when(cartRepository.findById("cart:" + custId)).thenReturn(Optional.of(mockedDbCart));
        when(cartRepository.save(any(Cart.class))).thenReturn(mockedDbCart);
    }

    @When("I attempt to update product {int} quantity to {double}")
    public void i_attempt_to_update_product_quantity_to(int productId, double newQuantity) {
        when(cartMapper.toResponse(any(Cart.class))).thenAnswer(inv -> {
            Cart saved = inv.getArgument(0);
            List<CartResponse.CartItemResponse> responses = saved.getItems().stream()
                    .map(i -> new CartResponse.CartItemResponse(i.getProductId(), i.getProductName(),
                            i.getProductDescription(), i.getUnitPrice(), i.getQuantity(), BigDecimal.TEN,
                            i.getAvailableQuantity()))
                    .toList();
            return new CartResponse(saved.getCartId(), saved.getCustomerId(), responses, BigDecimal.TEN, saved.getItems().size(), null, null);
        });
        try {
            cartService.updateItemQuantity(customerId, productId, newQuantity);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @When("I update product {int} quantity to {double}")
    public void i_update_product_quantity_to(int productId, double newQuantity) {
        when(cartMapper.toResponse(any(Cart.class))).thenAnswer(inv -> {
            Cart saved = inv.getArgument(0);
            List<CartResponse.CartItemResponse> responses = saved.getItems().stream()
                    .map(i -> new CartResponse.CartItemResponse(i.getProductId(), i.getProductName(),
                            i.getProductDescription(), i.getUnitPrice(), i.getQuantity(), BigDecimal.TEN,
                            i.getAvailableQuantity()))
                    .toList();
            return new CartResponse(saved.getCartId(), saved.getCustomerId(), responses, BigDecimal.TEN, saved.getItems().size(), null, null);
        });
        try {
            resultCart = cartService.updateItemQuantity(customerId, productId, newQuantity);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("the system should throw a stock limit validation error")
    public void the_system_should_throw_stock_limit_validation_error() {
        assertThat(caughtException).isNotNull();
        assertThat(caughtException).isInstanceOf(CartValidationException.class);
        assertThat(((CartValidationException) caughtException).getMessageKey())
                .isEqualTo("cart.item.exceeds.stock");
    }
}
