package code.with.vanilson.authentication.controller;

import code.with.vanilson.authentication.application.UpdateRoleRequest;
import code.with.vanilson.authentication.application.UserManagementService;
import code.with.vanilson.authentication.application.UserSummaryResponse;
import code.with.vanilson.authentication.config.JwtAuthFilter;
import code.with.vanilson.authentication.config.SecurityConfig;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import code.with.vanilson.authentication.presentation.UserManagementController;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserManagementController.class)
@Import(SecurityConfig.class)
@DisplayName("UserManagementController — WebMvc slice tests")
class UserManagementControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockBean UserManagementService  service;
    @MockBean JwtService             jwtService;
    @MockBean TokenRepository        tokenRepository;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthFilter          jwtAuthFilter;

    private static final String BASE = "/api/v1/auth/users";

    private UserDetailsAdapter adminPrincipal;
    private UserDetailsAdapter userPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        // Forward all requests past the JWT filter so Spring Security rules can run
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(
                    inv.<ServletRequest>getArgument(0),
                    inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());

        adminPrincipal = new UserDetailsAdapter(
                User.builder().id(1L).email("admin@x.com")
                        .role(Role.ADMIN).tenantId("system")
                        .firstname("Platform").lastname("Admin")
                        .password("hashed").accountEnabled(true).build());

        userPrincipal = new UserDetailsAdapter(
                User.builder().id(2L).email("user@x.com")
                        .role(Role.USER).tenantId("t1")
                        .firstname("Bob").lastname("Smith")
                        .password("hashed").accountEnabled(true).build());
    }

    // -------------------------------------------------------
    // GET /api/v1/auth/users
    // -------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/auth/users")
    class ListUsers {

        @Test
        @DisplayName("200 with user list for ADMIN")
        void admin_can_list_users() throws Exception {
            UserSummaryResponse summary = new UserSummaryResponse(
                    2L, "user@x.com", "Bob", "Smith", Role.USER, "t1", true);
            when(service.listUsers(any())).thenReturn(new PageImpl<>(List.of(summary)));

            mockMvc.perform(get(BASE).with(user(adminPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].email", is("user@x.com")))
                    .andExpect(jsonPath("$.content[0].role", is("USER")));
        }

        @Test
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_list_users() throws Exception {
            mockMvc.perform(get(BASE).with(user(userPrincipal)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("401 Unauthorized for anonymous request")
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -------------------------------------------------------
    // PATCH /api/v1/auth/users/{userId}/role
    // -------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auth/users/{userId}/role")
    class UpdateRole {

        @Test
        @DisplayName("200 with updated user when ADMIN promotes a USER to SELLER")
        void admin_can_promote_user() throws Exception {
            UserSummaryResponse updated = new UserSummaryResponse(
                    2L, "user@x.com", "Bob", "Smith", Role.SELLER, "t1", true);
            when(service.updateRole(any(), any(), any(), any())).thenReturn(updated);

            mockMvc.perform(patch(BASE + "/2/role")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Role.SELLER))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role", is("SELLER")))
                    .andExpect(jsonPath("$.email", is("user@x.com")));
        }

        @Test
        @DisplayName("403 Forbidden when USER tries to change a role")
        void user_cannot_update_role() throws Exception {
            mockMvc.perform(patch(BASE + "/3/role")
                            .with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Role.ADMIN))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("400 Bad Request when ADMIN tries to change own role")
        void admin_cannot_change_own_role() throws Exception {
            when(service.updateRole(any(), any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("Admin cannot change own role"));

            mockMvc.perform(patch(BASE + "/1/role")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Role.USER))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.bad.request")));
        }

        @Test
        @DisplayName("404 Not Found when target user does not exist")
        void returns_404_for_missing_user() throws Exception {
            when(service.updateRole(any(), any(), any(), any()))
                    .thenThrow(new AuthUserNotFoundException("User 99 not found", "auth.user.not.found"));

            mockMvc.perform(patch(BASE + "/99/role")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Role.SELLER))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("400 Bad Request when role field is null")
        void rejects_null_role_in_request_body() throws Exception {
            mockMvc.perform(patch(BASE + "/2/role")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\": null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("401 Unauthorized for anonymous request")
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(patch(BASE + "/2/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Role.SELLER))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
