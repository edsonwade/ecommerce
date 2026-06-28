package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderGlobalExceptionHandler;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderLineControllerTest — Web Layer (Slice) Tests
 * <p>
 * Covers: GET /api/v1/order-lines/{order-id}
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@WebMvcTest(OrderLineController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrderGlobalExceptionHandler.class)
@DisplayName("OrderLineController — Web Layer Tests")
class OrderLineControllerTest {

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderLineService orderLineService;

    @SuppressWarnings("unused")
    @MockBean
    private TenantHibernateFilterActivator activator;

    @MockBean
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("should return 200 with list of order lines for valid order ID")
    void shouldReturn200WithOrderLines() throws Exception {
        List<OrderLineResponse> responses = List.of(
                new OrderLineResponse(1, 100, 2.0),
                new OrderLineResponse(2, 200, 3.0));
        when(orderLineService.findAllByOrderId(42)).thenReturn(responses);

        mockMvc.perform(get("/api/v1/order-lines/{order-id}", 42)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].productId", is(100)))
                .andExpect(jsonPath("$[0].quantity", is(2.0)))
                .andExpect(jsonPath("$[1].id", is(2)));
    }

    @Test
    @DisplayName("should return 200 with empty list when no order lines exist")
    void shouldReturn200WithEmptyList() throws Exception {
        when(orderLineService.findAllByOrderId(99)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/order-lines/{order-id}", 99)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("seller: returns exactly the service's seller-scoped lines (other sellers' lines never reach the wire)")
    void shouldReturnOnlyTheSellerScopedLines() throws Exception {
        // The service has already filtered to the caller-seller's own lines; the controller
        // must surface only those — never the rest of the multi-seller basket.
        when(orderLineService.findAllByOrderId(500))
                .thenReturn(List.of(new OrderLineResponse(11, 9, 1.0)));

        mockMvc.perform(get("/api/v1/order-lines/{order-id}", 500)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].productId", is(9)));
    }

    @Test
    @DisplayName("should return 403 when the caller may not view the order's lines")
    void shouldReturn403WhenForbidden() throws Exception {
        when(orderLineService.findAllByOrderId(500))
                .thenThrow(new OrderForbiddenException("order.access.denied", "order.access.denied"));

        mockMvc.perform(get("/api/v1/order-lines/{order-id}", 500)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isForbidden());
    }
}
