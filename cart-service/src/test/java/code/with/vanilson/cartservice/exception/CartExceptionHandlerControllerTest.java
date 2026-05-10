package code.with.vanilson.cartservice.exception;

import code.with.vanilson.cartservice.application.CartService;
import code.with.vanilson.cartservice.presentation.CartController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level test — BUG-005: verifies cart endpoint 500 responses are clean.
 */
@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CartController — BUG-005 Error Sanitization (Controller Layer)")
class CartExceptionHandlerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @MockBean
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        when(messageSource.getMessage(eq("cart.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn("An unexpected error occurred. Please try again later.");
    }

    @Test
    @DisplayName("GET /api/v1/carts/{customerId} — unhandled exception returns 500 without UUID reference")
    void unhandledException_shouldReturn500WithCleanMessage() throws Exception {
        when(cartService.getCart("c-fail"))
                .thenThrow(new RuntimeException("Simulated failure"));

        mockMvc.perform(get("/api/v1/carts/{customerId}", "c-fail")
                        .header("X-Tenant-ID", "test-tenant"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.message", is("An unexpected error occurred. Please try again later.")))
                .andExpect(jsonPath("$.message", not(containsString("Reference:"))))
                .andExpect(jsonPath("$.errorCode", is("cart.error.internal")));
    }

    @Test
    @DisplayName("GET /api/v1/carts/{customerId} — error response must NOT contain requestId or reference fields")
    void unhandledException_shouldNotContainInternalFields() throws Exception {
        when(cartService.getCart("c-fail"))
                .thenThrow(new RuntimeException("Simulated error"));

        mockMvc.perform(get("/api/v1/carts/{customerId}", "c-fail")
                        .header("X-Tenant-ID", "test-tenant"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.reference").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }
}
