package code.with.vanilson.authentication.controller;

import code.with.vanilson.authentication.application.AuthService;
import code.with.vanilson.authentication.application.PasswordResetService;
import code.with.vanilson.authentication.config.JwtAuthFilter;
import code.with.vanilson.authentication.config.SecurityConfig;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import code.with.vanilson.authentication.presentation.AuthController;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("SecurityConfig — actuator endpoints (slice)")
class AuthActuatorSecurityTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuthService            authService;
    @MockBean PasswordResetService   passwordResetService;
    @MockBean JwtService             jwtService;
    @MockBean TokenRepository        tokenRepository;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthFilter          jwtAuthFilter;

    @BeforeEach
    void setUpFilterPassThrough() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
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
    @DisplayName("protected actuator endpoints still require authentication")
    void protectedActuatorEndpoint_stillRequiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
