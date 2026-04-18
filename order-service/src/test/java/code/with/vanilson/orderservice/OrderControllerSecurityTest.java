package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.config.OrderSecurityConfig;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
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
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({OrderSecurityConfig.class})
@DisplayName("OrderController — Security Tests")
class OrderControllerSecurityTest {

    @Autowired
    WebApplicationContext context;

    @MockBean OrderService                  orderService;
    @MockBean JwtAuthenticationFilter       jwtAuthenticationFilter;
    @MockBean TenantHibernateFilterActivator tenantHibernateFilterActivator;
    @MockBean JpaMetamodelMappingContext    jpaMetamodelMappingContext;

    private MockMvc mockMvc;

    private static final String BASE       = "/api/v1/orders";
    private static final String TENANT_HDR = "X-Tenant-ID";
    private static final String TENANT_VAL = "test-tenant-123";

    private static final String ORDER_BODY =
            "{\"amount\":100.00,\"paymentMethod\":\"CREDIT_CARD\",\"customerId\":\"42\"," +
            "\"products\":[{\"productId\":1,\"quantity\":1.0}]}";

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
                new SecurityPrincipal("user@test.com", userId, TENANT_VAL, role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    // -------------------------------------------------------
    // POST /orders — create order
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /orders — create order")
    class CreateOrder {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(post(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ORDER_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("202 when authenticated USER creates order")
        void authenticated_user_creates_order() throws Exception {
            when(orderService.createOrder(any())).thenReturn("corr-id-123");

            mockMvc.perform(post(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ORDER_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("202 when ADMIN creates order")
        void admin_creates_order() throws Exception {
            when(orderService.createOrder(any())).thenReturn("corr-id-456");

            mockMvc.perform(post(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ORDER_BODY)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isAccepted());
        }
    }

    // -------------------------------------------------------
    // GET /orders — list all (ADMIN only)
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /orders — list all (ADMIN only)")
    class ListOrders {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE).header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER tries to list all orders")
        void user_cannot_list_all_orders() throws Exception {
            mockMvc.perform(get(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN lists all orders")
        void admin_lists_all_orders() throws Exception {
            when(orderService.findAllOrders()).thenReturn(List.of(
                    new OrderResponse(1, "REF-001", BigDecimal.valueOf(100), "CREDIT_CARD", "42")));

            mockMvc.perform(get(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // GET /orders/{id} — get by ID (ownership-checked in service)
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /orders/{id} — get by ID")
    class GetById {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/1").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 when USER accesses their own order")
        void user_accesses_own_order() throws Exception {
            when(orderService.findById(1)).thenReturn(
                    new OrderResponse(1, "REF-001", BigDecimal.valueOf(100), "CREDIT_CARD", "42"));

            mockMvc.perform(get(BASE + "/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 when USER accesses another user's order (service throws)")
        void user_cannot_access_foreign_order() throws Exception {
            doThrow(new OrderForbiddenException("order.access.denied", "order.access.denied"))
                    .when(orderService).findById(2);

            mockMvc.perform(get(BASE + "/2")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN accesses any order")
        void admin_accesses_any_order() throws Exception {
            when(orderService.findById(anyInt())).thenReturn(
                    new OrderResponse(99, "REF-099", BigDecimal.valueOf(200), "CREDIT_CARD", "99"));

            mockMvc.perform(get(BASE + "/99")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // GET /orders/status/{correlationId}
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /orders/status/{correlationId} — poll status")
    class GetStatus {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/status/corr-abc").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 when authenticated user polls status")
        void authenticated_user_polls_status() throws Exception {
            when(orderService.getOrderStatus("corr-abc")).thenReturn(
                    new OrderStatusResponse(1, "corr-abc", "REF-001", "REQUESTED",
                            BigDecimal.valueOf(100), null));

            mockMvc.perform(get(BASE + "/status/corr-abc")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("404 when correlation ID not found")
        void not_found_returns_404() throws Exception {
            doThrow(new OrderNotFoundException("order.not.found", "order.not.found"))
                    .when(orderService).getOrderStatus("bad-id");

            mockMvc.perform(get(BASE + "/status/bad-id")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isNotFound());
        }
    }
}
