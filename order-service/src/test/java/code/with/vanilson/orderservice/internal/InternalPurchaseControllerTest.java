package code.with.vanilson.orderservice.internal;

import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.exception.OrderGlobalExceptionHandler;
import code.with.vanilson.orderservice.orderLine.OrderLineRepository;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
import java.util.Locale;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalPurchaseControllerTest — Web Layer (Slice) Tests.
 * <p>
 * Covers GET /api/v1/orders/internal/purchases/exists. Filters are disabled here
 * ({@code addFilters = false}); the shared-secret guard itself is covered by
 * {@link InternalTokenFilterTest} and the integration test. This slice proves the
 * controller maps the repository's boolean into {@code {"purchased": …}}.
 *
 * @author vamuhong
 * @version 1.0
 */
@WebMvcTest(InternalPurchaseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrderGlobalExceptionHandler.class)
@DisplayName("InternalPurchaseController — Web Layer Tests")
class InternalPurchaseControllerTest {

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderLineRepository orderLineRepository;

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
    @DisplayName("returns {\"purchased\":true} when the customer has a fulfilled purchase")
    void returnsTrueWhenPurchased() throws Exception {
        when(orderLineRepository.existsPurchasedProduct(eq("42"), eq(1), any(Collection.class)))
                .thenReturn(true);

        mockMvc.perform(get("/api/v1/orders/internal/purchases/exists")
                        .param("customerId", "42")
                        .param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased", is(true)));
    }

    @Test
    @DisplayName("returns {\"purchased\":false} when the customer never bought the product")
    void returnsFalseWhenNotPurchased() throws Exception {
        when(orderLineRepository.existsPurchasedProduct(eq("42"), eq(999), any(Collection.class)))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/orders/internal/purchases/exists")
                        .param("customerId", "42")
                        .param("productId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased", is(false)));
    }

    @Test
    @DisplayName("passes only fulfilled statuses (CONFIRMED/SHIPPED/DELIVERED) to the repository")
    void passesFulfilledStatuses() throws Exception {
        when(orderLineRepository.existsPurchasedProduct(eq("42"), eq(1), any(Collection.class)))
                .thenAnswer(inv -> {
                    Collection<OrderStatus> statuses = inv.getArgument(2);
                    return statuses.contains(OrderStatus.CONFIRMED)
                            && statuses.contains(OrderStatus.SHIPPED)
                            && statuses.contains(OrderStatus.DELIVERED)
                            && !statuses.contains(OrderStatus.REQUESTED)
                            && !statuses.contains(OrderStatus.CANCELLED);
                });

        mockMvc.perform(get("/api/v1/orders/internal/purchases/exists")
                        .param("customerId", "42")
                        .param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased", is(true)));
    }
}
