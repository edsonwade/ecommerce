package code.with.vanilson.paymentservice.presentation;

import code.with.vanilson.paymentservice.application.PaymentResponse;
import code.with.vanilson.paymentservice.application.PaymentService;
import code.with.vanilson.paymentservice.config.PaymentSecurityConfig;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(PaymentSecurityConfig.class)
@DisplayName("PaymentController — Security Tests")
class PaymentControllerSecurityTest {

    @Autowired
    WebApplicationContext context;

    @MockBean PaymentService                 paymentService;
    @MockBean JwtAuthenticationFilter        jwtAuthenticationFilter;
    @MockBean TenantHibernateFilterActivator tenantHibernateFilterActivator;
    @MockBean JpaMetamodelMappingContext     jpaMetamodelMappingContext;

    private MockMvc mockMvc;

    private static final String BASE       = "/api/v1/payments";
    private static final String TENANT_HDR = "X-Tenant-ID";
    private static final String TENANT_VAL = "test-tenant-123";

    private static final String PAYMENT_BODY =
            "{\"orderId\":1,\"orderReference\":\"REF-001\",\"amount\":100.00," +
            "\"paymentMethod\":\"CREDIT_CARD\"," +
            "\"customer\":{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@example.com\"}}";

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

    private PaymentResponse samplePayment() {
        return new PaymentResponse(1, BigDecimal.valueOf(100), "CREDIT_CARD", 1, "REF-001", LocalDateTime.now());
    }

    // -------------------------------------------------------
    // POST /payments — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /payments — ADMIN only")
    class CreatePayment {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(post(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PAYMENT_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER tries to create payment")
        void user_cannot_create_payment() throws Exception {
            mockMvc.perform(post(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PAYMENT_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("403 when SELLER tries to create payment")
        void seller_cannot_create_payment() throws Exception {
            mockMvc.perform(post(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PAYMENT_BODY)
                            .with(authentication(authAs(5L, "SELLER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("201 when ADMIN creates payment")
        void admin_can_create_payment() throws Exception {
            when(paymentService.createPayment(any())).thenReturn(1);

            mockMvc.perform(post(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PAYMENT_BODY)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isCreated());
        }
    }

    // -------------------------------------------------------
    // GET /payments — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /payments — list all (ADMIN only)")
    class FindAll {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE).header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER lists payments")
        void user_cannot_list_payments() throws Exception {
            mockMvc.perform(get(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN lists payments")
        void admin_lists_payments() throws Exception {
            when(paymentService.findAllPayments()).thenReturn(List.of(samplePayment()));

            mockMvc.perform(get(BASE)
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // GET /payments/{id} — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /payments/{id} — get by ID (ADMIN only)")
    class FindById {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/1").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER accesses payment")
        void user_cannot_access_payment() throws Exception {
            mockMvc.perform(get(BASE + "/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN accesses payment")
        void admin_can_access_payment() throws Exception {
            when(paymentService.findById(anyInt())).thenReturn(samplePayment());

            mockMvc.perform(get(BASE + "/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }
}
