package code.with.vanilson.orderservice.exception;

import code.with.vanilson.orderservice.OrderController;
import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level test — BUG-005: verifies that unhandled exceptions
 * return a clean 500 response through MockMvc WITHOUT UUID references.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OrderController — BUG-005 Error Sanitization (Controller Layer)")
class OrderExceptionHandlerControllerTest {

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @SuppressWarnings("unused")
    @MockBean
    private TenantHibernateFilterActivator activator;

    @MockBean
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        when(messageSource.getMessage(eq("order.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn("An unexpected error occurred. Please try again later.");
        // Fallback for any other message key
        when(messageSource.getMessage(eq("order.error.internal"), any(), any(Locale.class)))
                .thenReturn("order.error.internal");
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} — unhandled exception returns 500 without UUID reference")
    void unhandledException_shouldReturn500WithCleanMessage() throws Exception {
        when(orderService.findById(999))
                .thenThrow(new RuntimeException("Simulated NullPointerException"));

        mockMvc.perform(get("/api/v1/orders/{order-id}", 999)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.message", is("An unexpected error occurred. Please try again later.")))
                .andExpect(jsonPath("$.message", not(containsString("Reference:"))))
                .andExpect(jsonPath("$.errorCode", is("order.error.internal")));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} — error response must NOT contain requestId or reference fields")
    void unhandledException_shouldNotContainInternalFields() throws Exception {
        when(orderService.findById(999))
                .thenThrow(new RuntimeException("Simulated error"));

        mockMvc.perform(get("/api/v1/orders/{order-id}", 999)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.reference").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }
}
