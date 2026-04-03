package code.with.vanilson.cartservice.presentation;

import code.with.vanilson.cartservice.application.AddCartItemRequest;
import code.with.vanilson.cartservice.application.CartResponse;
import code.with.vanilson.cartservice.application.CartService;
import code.with.vanilson.cartservice.exception.CartNotFoundException;
import code.with.vanilson.cartservice.exception.CartValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@DisplayName("CartController — Web Layer Tests")
class CartControllerTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    private AddCartItemRequest validRequest;
    private CartResponse cartResponse;

    @BeforeEach
    void setUp() {
        validRequest = new AddCartItemRequest(1, "Laptop", "Gaming Laptop", BigDecimal.valueOf(1200), 2.0);
        CartResponse.CartItemResponse item = new CartResponse.CartItemResponse(
                1, "Laptop", "Gaming Laptop", BigDecimal.valueOf(1200), 2.0, BigDecimal.valueOf(2400));
        cartResponse = new CartResponse("cart:c-01", "c-01", List.of(item),
                BigDecimal.valueOf(2400), 1, LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /api/v1/carts/{customerId}")
    class GetCart {
        @Test
        @DisplayName("should return 200 with cart response when found")
        void shouldReturn200() throws Exception {
            when(cartService.getCart("c-01")).thenReturn(cartResponse);

            mockMvc.perform(get("/api/v1/carts/{customerId}", "c-01")
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is("c-01")))
                    .andExpect(jsonPath("$.itemCount", is(1)));
        }

        @Test
        @DisplayName("should return 404 when cart not found")
        void shouldReturn404() throws Exception {
            when(cartService.getCart("unknown")).thenThrow(new CartNotFoundException("Not found", "cart.not.found"));

            mockMvc.perform(get("/api/v1/carts/{customerId}", "unknown")
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/carts/{customerId}/items")
    class AddItem {
        @Test
        @DisplayName("should return 201 when item added")
        void shouldReturn201() throws Exception {
            when(cartService.addItem(eq("c-01"), any(AddCartItemRequest.class))).thenReturn(cartResponse);

            mockMvc.perform(post("/api/v1/carts/{customerId}/items", "c-01")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerId", is("c-01")))
                    .andExpect(jsonPath("$.itemCount", is(1)));
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        void shouldReturn400() throws Exception {
            AddCartItemRequest invalid = new AddCartItemRequest(null, "", "", null, -1.0);

            mockMvc.perform(post("/api/v1/carts/{customerId}/items", "c-01")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/carts/{customerId}/items/{productId}")
    class UpdateQuantity {
        @Test
        @DisplayName("should return 200 when quantity updated")
        void shouldReturn200() throws Exception {
            when(cartService.updateItemQuantity(anyString(), anyInt(), anyDouble())).thenReturn(cartResponse);

            mockMvc.perform(patch("/api/v1/carts/{customerId}/items/{productId}", "c-01", 1)
                            .header(TENANT_HEADER, TENANT_ID)
                            .param("quantity", "5.0"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 when quantity is missing or negative")
        void shouldReturn400() throws Exception {
            mockMvc.perform(patch("/api/v1/carts/{customerId}/items/{productId}", "c-01", 1)
                            .header(TENANT_HEADER, TENANT_ID)
                            .param("quantity", "-5.0"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/carts/{customerId}/items/{productId}")
    class RemoveItem {
        @Test
        @DisplayName("should return 200 and updated cart")
        void shouldReturn200() throws Exception {
            when(cartService.removeItem("c-01", 1)).thenReturn(cartResponse);

            mockMvc.perform(delete("/api/v1/carts/{customerId}/items/{productId}", "c-01", 1)
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/carts/{customerId}")
    class ClearCart {
        @Test
        @DisplayName("should return 204 when cart cleared")
        void shouldReturn204() throws Exception {
            doNothing().when(cartService).clearCart("c-01");

            mockMvc.perform(delete("/api/v1/carts/{customerId}", "c-01")
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/carts/{customerId}/checkout")
    class CheckoutSnapshot {
        @Test
        @DisplayName("should return 200 with snapshot")
        void shouldReturn200() throws Exception {
            when(cartService.checkoutSnapshot("c-01")).thenReturn(cartResponse);

            mockMvc.perform(get("/api/v1/carts/{customerId}/checkout", "c-01")
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 when cart is empty")
        void shouldReturn400() throws Exception {
            when(cartService.checkoutSnapshot("c-empty"))
                    .thenThrow(new CartValidationException("Empty cart", "cart.empty"));

            mockMvc.perform(get("/api/v1/carts/{customerId}/checkout", "c-empty")
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isBadRequest());
        }
    }
}
