package code.with.vanilson.productservice.exception.handler;

import code.with.vanilson.productservice.ProductController;
import code.with.vanilson.productservice.ProductMapper;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.config.ProductSecurityConfig;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level test — BUG-005: verifies product endpoint 500 responses are clean.
 */
@WebMvcTest(ProductController.class)
@Import(ProductSecurityConfig.class)
@DisplayName("ProductController — BUG-005 Error Sanitization (Controller Layer)")
class ProductExceptionHandlerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private ProductMapper productMapper;

    @SuppressWarnings("unused")
    @MockBean
    private TenantHibernateFilterActivator activator;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private MessageSource messageSource;

    @BeforeEach
    void setUp() throws Exception {
        when(messageSource.getMessage(eq("product.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn("An unexpected error occurred. Please try again later.");

        // Pass JWT filter through
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(
                    inv.<ServletRequest>getArgument(0),
                    inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/products/{id} — unhandled exception returns 500 without UUID reference")
    void unhandledException_shouldReturn500WithCleanMessage() throws Exception {
        when(productService.getProductById(999))
                .thenThrow(new RuntimeException("Simulated failure"));

        mockMvc.perform(get("/api/v1/products/{id}", 999)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.message", is("An unexpected error occurred. Please try again later.")))
                .andExpect(jsonPath("$.message", not(containsString("Reference:"))))
                .andExpect(jsonPath("$.errorCode", is("product.error.internal")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/products/{id} — error response must NOT contain requestId or reference fields")
    void unhandledException_shouldNotContainInternalFields() throws Exception {
        when(productService.getProductById(999))
                .thenThrow(new RuntimeException("Simulated error"));

        mockMvc.perform(get("/api/v1/products/{id}", 999)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.reference").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }
}
