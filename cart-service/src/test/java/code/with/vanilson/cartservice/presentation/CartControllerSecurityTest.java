package code.with.vanilson.cartservice.presentation;

import code.with.vanilson.cartservice.application.CartResponse;
import code.with.vanilson.cartservice.application.CartService;
import code.with.vanilson.cartservice.config.CartSecurityConfig;
import code.with.vanilson.tenantcontext.security.JwtAuthenticationFilter;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(CartSecurityConfig.class)
@DisplayName("CartController — Security Tests")
class CartControllerSecurityTest {

    @Autowired
    WebApplicationContext context;

    @MockBean
    CartService cartService;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockMvc mockMvc;

    private static final String BASE       = "/api/v1/carts";
    private static final String TENANT_HDR = "X-Tenant-ID";
    private static final String TENANT_VAL = "test-tenant-123";

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(get("/").header(TENANT_HDR, TENANT_VAL))
                .build();

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private static UsernamePasswordAuthenticationToken authAs(long userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                new SecurityPrincipal("user@test.com", userId, "test-tenant-123", role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private CartResponse sampleCart(String customerId) {
        CartResponse.CartItemResponse item = new CartResponse.CartItemResponse(
                1, "Laptop", "Gaming Laptop", BigDecimal.valueOf(1200), 1.0, BigDecimal.valueOf(1200));
        return new CartResponse("cart:" + customerId, customerId, List.of(item),
                BigDecimal.valueOf(1200), 1, LocalDateTime.now(), LocalDateTime.now());
    }

    // -------------------------------------------------------
    // GET /carts/{customerId}
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /{customerId} — get cart")
    class GetCart {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/42").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 when USER accesses own cart")
        void user_own_cart_ok() throws Exception {
            when(cartService.getCart("42")).thenReturn(sampleCart("42"));

            mockMvc.perform(get(BASE + "/42")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 when USER accesses another user's cart")
        void user_foreign_cart_forbidden() throws Exception {
            mockMvc.perform(get(BASE + "/99")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN accesses any cart")
        void admin_any_cart_ok() throws Exception {
            when(cartService.getCart("99")).thenReturn(sampleCart("99"));

            mockMvc.perform(get(BASE + "/99")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // POST /carts/{customerId}/items
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /{customerId}/items — add item")
    class AddItem {

        private static final String VALID_BODY =
                "{\"productId\":1,\"productName\":\"Laptop\",\"productDescription\":\"Gaming\",\"unitPrice\":1200.0,\"quantity\":1.0}";

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(post(BASE + "/42/items")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("201 when USER adds to own cart")
        void user_adds_to_own_cart() throws Exception {
            when(cartService.addItem(anyString(), any())).thenReturn(sampleCart("42"));

            mockMvc.perform(post(BASE + "/42/items")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("403 when USER adds to another user's cart")
        void user_cannot_add_to_foreign_cart() throws Exception {
            mockMvc.perform(post(BASE + "/99/items")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("201 when ADMIN adds to any cart")
        void admin_adds_to_any_cart() throws Exception {
            when(cartService.addItem(anyString(), any())).thenReturn(sampleCart("99"));

            mockMvc.perform(post(BASE + "/99/items")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isCreated());
        }
    }

    // -------------------------------------------------------
    // PATCH /carts/{customerId}/items/{productId}
    // -------------------------------------------------------

    @Nested
    @DisplayName("PATCH /{customerId}/items/{productId} — update quantity")
    class UpdateQuantity {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(patch(BASE + "/42/items/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .param("quantity", "2.0"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 when USER updates own cart item")
        void user_updates_own_cart() throws Exception {
            when(cartService.updateItemQuantity(anyString(), anyInt(), anyDouble()))
                    .thenReturn(sampleCart("42"));

            mockMvc.perform(patch(BASE + "/42/items/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .param("quantity", "2.0")
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 when USER updates another user's cart")
        void user_cannot_update_foreign_cart() throws Exception {
            mockMvc.perform(patch(BASE + "/99/items/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .param("quantity", "2.0")
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }
    }

    // -------------------------------------------------------
    // DELETE /carts/{customerId}/items/{productId}
    // -------------------------------------------------------

    @Nested
    @DisplayName("DELETE /{customerId}/items/{productId} — remove item")
    class RemoveItem {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(delete(BASE + "/42/items/1").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 when USER removes from own cart")
        void user_removes_from_own_cart() throws Exception {
            when(cartService.removeItem(anyString(), anyInt())).thenReturn(sampleCart("42"));

            mockMvc.perform(delete(BASE + "/42/items/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 when USER removes from another user's cart")
        void user_cannot_remove_from_foreign_cart() throws Exception {
            mockMvc.perform(delete(BASE + "/99/items/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }
    }

    // -------------------------------------------------------
    // DELETE /carts/{customerId}
    // -------------------------------------------------------

    @Nested
    @DisplayName("DELETE /{customerId} — clear cart")
    class ClearCart {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(delete(BASE + "/42").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("204 when USER clears own cart")
        void user_clears_own_cart() throws Exception {
            doNothing().when(cartService).clearCart(anyString());

            mockMvc.perform(delete(BASE + "/42")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("403 when USER clears another user's cart")
        void user_cannot_clear_foreign_cart() throws Exception {
            mockMvc.perform(delete(BASE + "/99")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("204 when ADMIN clears any cart")
        void admin_clears_any_cart() throws Exception {
            doNothing().when(cartService).clearCart(anyString());

            mockMvc.perform(delete(BASE + "/99")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isNoContent());
        }
    }

    // -------------------------------------------------------
    // GET /carts/{customerId}/checkout
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /{customerId}/checkout — checkout snapshot")
    class CheckoutSnapshot {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/42/checkout").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 when USER checks out own cart")
        void user_own_checkout_ok() throws Exception {
            when(cartService.checkoutSnapshot("42")).thenReturn(sampleCart("42"));

            mockMvc.perform(get(BASE + "/42/checkout")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 when USER checks out another user's cart")
        void user_cannot_checkout_foreign_cart() throws Exception {
            mockMvc.perform(get(BASE + "/99/checkout")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN checks out any cart")
        void admin_any_checkout_ok() throws Exception {
            when(cartService.checkoutSnapshot("99")).thenReturn(sampleCart("99"));

            mockMvc.perform(get(BASE + "/99/checkout")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }
}
