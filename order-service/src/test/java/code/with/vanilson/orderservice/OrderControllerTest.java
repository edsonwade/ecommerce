package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.exception.CustomerServiceUnavailableException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.orderservice.product.ProductPurchaseRequest;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderControllerTest — Web Layer (Slice) Tests
 * <p>
 * Uses @WebMvcTest — controller + exception handler only. No DB, no Kafka.
 * Covers: POST (202), GET status (200/404), GET all, GET by id (200/404).
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@WebMvcTest(OrderController.class)
@DisplayName("OrderController — Web Layer Tests")
class OrderControllerTest {

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @SuppressWarnings("unused")
    @MockBean
    private TenantHibernateFilterActivator activator;

    @MockBean
    private MessageSource messageSource;

    private OrderRequest validRequest;
    private OrderResponse orderResponse;
    private OrderStatusResponse statusResponse;

    @BeforeEach
    void setUp() {
        validRequest = new OrderRequest(
                null,
                "REF-001",
                BigDecimal.valueOf(299.99),
                PaymentMethod.CREDIT_CARD,
                "cust-001",
                List.of(new ProductPurchaseRequest(1, 2.0))
        );

        orderResponse = new OrderResponse(
                42, "REF-001", BigDecimal.valueOf(299.99), "CREDIT_CARD", "cust-001");

        statusResponse = new OrderStatusResponse(
                42, "corr-id-001", "REF-001", "REQUESTED",
                BigDecimal.valueOf(299.99), LocalDateTime.now());

        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------
    // POST /api/v1/orders
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/orders")
    class CreateOrder {

        @Test
        @DisplayName("should return 202 Accepted with correlationId when valid request")
        void shouldReturn202WithCorrelationId() throws Exception {
            when(orderService.createOrder(any(OrderRequest.class))).thenReturn("corr-id-001");

            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.correlationId", is("corr-id-001")))
                    .andExpect(jsonPath("$.status", is("REQUESTED")));
        }

        @Test
        @DisplayName("should return 503 when customer service is unavailable")
        void shouldReturn503WhenCustomerUnavailable() throws Exception {
            when(orderService.createOrder(any(OrderRequest.class)))
                    .thenThrow(new CustomerServiceUnavailableException("Unavailable", "order.customer.not.found"));

            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("should return 400 when amount is null")
        void shouldReturn400WhenAmountNull() throws Exception {
            OrderRequest invalid = new OrderRequest(
                    null, "REF-001", null, PaymentMethod.CREDIT_CARD, "cust-001",
                    List.of(new ProductPurchaseRequest(1, 2.0)));

            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when products list is empty")
        void shouldReturn400WhenProductsEmpty() throws Exception {
            OrderRequest invalid = new OrderRequest(
                    null, "REF-001", BigDecimal.TEN, PaymentMethod.VISA, "cust-001", List.of());

            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when customerId is blank")
        void shouldReturn400WhenCustomerIdBlank() throws Exception {
            OrderRequest invalid = new OrderRequest(
                    null, "REF-001", BigDecimal.TEN, PaymentMethod.VISA, "",
                    List.of(new ProductPurchaseRequest(1, 1.0)));

            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Tenant-ID", "test-tenant-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------
    // GET /api/v1/orders/status/{correlationId}
    // -------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/orders/status/{correlationId}")
    class GetOrderStatus {

        @Test
        @DisplayName("should return 200 with order status when found")
        void shouldReturn200WithStatus() throws Exception {
            when(orderService.getOrderStatus("corr-id-001")).thenReturn(statusResponse);

            mockMvc.perform(get("/api/v1/orders/status/{correlationId}", "corr-id-001")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correlationId", is("corr-id-001")))
                    .andExpect(jsonPath("$.status", is("REQUESTED")));
        }

        @Test
        @DisplayName("should return 404 when correlationId not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(orderService.getOrderStatus("unknown"))
                    .thenThrow(new OrderNotFoundException("Not found", "order.not.found"));

            mockMvc.perform(get("/api/v1/orders/status/{correlationId}", "unknown")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------
    // GET /api/v1/orders
    // -------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/orders")
    class FindAllOrders {

        @Test
        @DisplayName("should return 200 with list of orders")
        void shouldReturn200WithOrderList() throws Exception {
            when(orderService.findAllOrders()).thenReturn(List.of(orderResponse));

            mockMvc.perform(get("/api/v1/orders")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(42)))
                    .andExpect(jsonPath("$[0].reference", is("REF-001")));
        }

        @Test
        @DisplayName("should return 200 with empty list when no orders exist")
        void shouldReturn200WithEmptyList() throws Exception {
            when(orderService.findAllOrders()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/orders")
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // -------------------------------------------------------
    // GET /api/v1/orders/{order-id}
    // -------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/orders/{order-id}")
    class FindById {

        @Test
        @DisplayName("should return 200 with order when found")
        void shouldReturn200WithOrder() throws Exception {
            when(orderService.findById(42)).thenReturn(orderResponse);

            mockMvc.perform(get("/api/v1/orders/{order-id}", 42)
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(42)))
                    .andExpect(jsonPath("$.reference", is("REF-001")))
                    .andExpect(jsonPath("$.customerId", is("cust-001")));
        }

        @Test
        @DisplayName("should return 404 when order not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(orderService.findById(999))
                    .thenThrow(new OrderNotFoundException("Not found", "order.not.found"));

            mockMvc.perform(get("/api/v1/orders/{order-id}", 999)
                            .header("X-Tenant-ID", "test-tenant-123"))
                    .andExpect(status().isNotFound());
        }
    }
}
