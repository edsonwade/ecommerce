package code.with.vanilson.tenantservice.controller;

import code.with.vanilson.tenantservice.application.TenantResponse;
import code.with.vanilson.tenantservice.application.TenantService;
import code.with.vanilson.tenantservice.application.TenantUsageMetricService;
import code.with.vanilson.tenantservice.config.TenantSecurityConfig;
import code.with.vanilson.tenantservice.presentation.TenantController;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import(TenantSecurityConfig.class)
@DisplayName("TenantController — Security Tests")
class TenantControllerSecurityTest {

    @Autowired
    WebApplicationContext context;

    @MockBean TenantService              tenantService;
    @MockBean TenantUsageMetricService   usageMetricService;
    @MockBean JwtAuthenticationFilter    jwtAuthenticationFilter;
    @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private MockMvc mockMvc;

    private static final String BASE = "/api/v1/tenants";

    private static final String CREATE_BODY =
            "{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"contactEmail\":\"admin@acme.com\",\"plan\":\"FREE\"}";

    private static final String UPDATE_BODY =
            "{\"name\":\"Acme Corp Updated\",\"contactEmail\":\"admin@acme.com\"}";

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private static UsernamePasswordAuthenticationToken authAs(long userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                new SecurityPrincipal("user@test.com", userId, "system", role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private TenantResponse sampleTenant(String id) {
        return new TenantResponse(id, "Acme Corp", "acme-corp", "admin@acme.com",
                "FREE", "ACTIVE", 100, 1000L, LocalDateTime.now(), LocalDateTime.now());
    }

    // -------------------------------------------------------
    // GET /{tenantId}/validate — public (gateway bootstrap)
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /{tenantId}/validate — public")
    class ValidateTenant {

        @Test
        @DisplayName("200 when unauthenticated — validate is permitAll")
        void unauthenticated_can_validate() throws Exception {
            when(tenantService.validateTenant(anyString())).thenReturn(sampleTenant("t-1"));
            mockMvc.perform(get(BASE + "/t-1/validate"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 when ADMIN validates")
        void admin_can_validate() throws Exception {
            when(tenantService.validateTenant(anyString())).thenReturn(sampleTenant("t-1"));
            mockMvc.perform(get(BASE + "/t-1/validate").with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // GET /tenants — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /tenants — list all (ADMIN only)")
    class ListAll {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER lists tenants")
        void user_cannot_list_tenants() throws Exception {
            mockMvc.perform(get(BASE).with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("403 when SELLER lists tenants")
        void seller_cannot_list_tenants() throws Exception {
            mockMvc.perform(get(BASE).with(authentication(authAs(5L, "SELLER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN lists tenants")
        void admin_lists_tenants() throws Exception {
            when(tenantService.findAll()).thenReturn(List.of(sampleTenant("t-1")));
            mockMvc.perform(get(BASE).with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // GET /tenants/{id} — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /tenants/{tenantId} — ADMIN only")
    class GetById {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/t-1")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER fetches tenant")
        void user_cannot_get_tenant() throws Exception {
            mockMvc.perform(get(BASE + "/t-1").with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN fetches tenant")
        void admin_gets_tenant() throws Exception {
            when(tenantService.findByTenantId("t-1")).thenReturn(sampleTenant("t-1"));
            mockMvc.perform(get(BASE + "/t-1").with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // POST /tenants — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /tenants — create (ADMIN only)")
    class CreateTenant {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER creates tenant")
        void user_cannot_create_tenant() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("201 when ADMIN creates tenant")
        void admin_can_create_tenant() throws Exception {
            when(tenantService.createTenant(any())).thenReturn(sampleTenant("t-new"));
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isCreated());
        }
    }

    // -------------------------------------------------------
    // PUT /tenants/{id} — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("PUT /tenants/{tenantId} — update (ADMIN only)")
    class UpdateTenant {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(put(BASE + "/t-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER updates tenant")
        void user_cannot_update_tenant() throws Exception {
            mockMvc.perform(put(BASE + "/t-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN updates tenant")
        void admin_can_update_tenant() throws Exception {
            when(tenantService.updateTenant(anyString(), any())).thenReturn(sampleTenant("t-1"));
            mockMvc.perform(put(BASE + "/t-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // PATCH /tenants/{id}/suspend — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("PATCH /tenants/{tenantId}/suspend — ADMIN only")
    class SuspendTenant {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(patch(BASE + "/t-1/suspend")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER suspends tenant")
        void user_cannot_suspend_tenant() throws Exception {
            mockMvc.perform(patch(BASE + "/t-1/suspend").with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("204 when ADMIN suspends tenant")
        void admin_can_suspend_tenant() throws Exception {
            mockMvc.perform(patch(BASE + "/t-1/suspend").with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isNoContent());
        }
    }

    // -------------------------------------------------------
    // PATCH /tenants/{id}/reactivate — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("PATCH /tenants/{tenantId}/reactivate — ADMIN only")
    class ReactivateTenant {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(patch(BASE + "/t-1/reactivate")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER reactivates tenant")
        void user_cannot_reactivate_tenant() throws Exception {
            mockMvc.perform(patch(BASE + "/t-1/reactivate").with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("204 when ADMIN reactivates tenant")
        void admin_can_reactivate_tenant() throws Exception {
            mockMvc.perform(patch(BASE + "/t-1/reactivate").with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isNoContent());
        }
    }

    // -------------------------------------------------------
    // DELETE /tenants/{id} — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("DELETE /tenants/{tenantId} — ADMIN only")
    class DeleteTenant {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(delete(BASE + "/t-1")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER deletes tenant")
        void user_cannot_delete_tenant() throws Exception {
            mockMvc.perform(delete(BASE + "/t-1").with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("204 when ADMIN deletes tenant")
        void admin_can_delete_tenant() throws Exception {
            mockMvc.perform(delete(BASE + "/t-1").with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isNoContent());
        }
    }
}
