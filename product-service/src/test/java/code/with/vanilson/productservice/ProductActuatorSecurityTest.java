package code.with.vanilson.productservice;

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
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-layer slice: proves the filter chain lets unauthenticated requests
 * reach /actuator/prometheus. Actuator itself is not mapped in a @WebMvcTest
 * slice, so a permitted request falls through to 404/500 handling — only a
 * 401 means security blocked it.
 * <p>
 * The public catalogue (GET /api/v1/products) is deliberately unauthenticated,
 * so the "still requires auth" guard uses /api/v1/products/mine (seller-only).
 */
@WebMvcTest(ProductController.class)
@Import(ProductSecurityConfig.class)
@DisplayName("ProductSecurityConfig — actuator endpoints (slice)")
class ProductActuatorSecurityTest {

    @Autowired WebApplicationContext context;

    @MockBean ProductService                 productService;
    @MockBean ProductMapper                  productMapper;
    @MockBean JwtAuthenticationFilter        jwtAuthenticationFilter;
    @MockBean TenantHibernateFilterActivator tenantHibernateFilterActivator;

    private static final String TENANT_HDR = "X-Tenant-ID";
    private static final String TENANT_VAL = "test-tenant-123";

    private MockMvc mockMvc;

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

    @Test
    @DisplayName("/actuator/prometheus is not blocked by security (no 401)")
    void prometheusScrape_isPermitted() throws Exception {
        int status = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
    }

    @Test
    @DisplayName("/actuator/health stays public (regression guard)")
    void health_isPermitted() throws Exception {
        int status = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
    }

    @Test
    @DisplayName("seller-only endpoint still requires authentication")
    void sellerEndpoint_stillRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/products/mine"))
                .andExpect(status().isUnauthorized());
    }
}
