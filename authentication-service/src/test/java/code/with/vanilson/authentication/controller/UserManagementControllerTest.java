package code.with.vanilson.authentication.controller;

import code.with.vanilson.authentication.application.AdminCreateUserRequest;
import code.with.vanilson.authentication.application.AdminUpdateUserRequest;
import code.with.vanilson.authentication.application.UpdateRoleRequest;
import code.with.vanilson.authentication.application.UpdateSellerStatusRequest;
import code.with.vanilson.authentication.application.UpdateUserStatusRequest;
import code.with.vanilson.authentication.application.UserManagementService;
import code.with.vanilson.authentication.application.UserSummaryResponse;
import code.with.vanilson.authentication.exception.AdminActionNotAllowedException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.config.JwtAuthFilter;
import code.with.vanilson.authentication.config.SecurityConfig;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.SellerStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserManagementController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("UserManagementController - WebMvc slice tests")
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
                    2L, "user@x.com", "Bob", "Smith", Role.USER, "t1", true, null);
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
    // POST /api/v1/auth/users
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/users")
    class CreateUser {

        private String body(String role) throws Exception {
            return objectMapper.writeValueAsString(new AdminCreateUserRequest(
                    "New", "User", "new.user@x.com", "Password1!",
                    role == null ? null : Role.valueOf(role), "default"));
        }

        @Test
        @DisplayName("201 Created when ADMIN creates a SELLER")
        void admin_can_create_seller() throws Exception {
            UserSummaryResponse created = new UserSummaryResponse(
                    42L, "new.user@x.com", "New", "User", Role.SELLER, "default", true,
                    SellerStatus.APPROVED);
            when(service.createUser(any(), any())).thenReturn(created);

            mockMvc.perform(post(BASE)
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("SELLER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(42)))
                    .andExpect(jsonPath("$.email", is("new.user@x.com")))
                    .andExpect(jsonPath("$.role", is("SELLER")))
                    .andExpect(jsonPath("$.sellerStatus", is("APPROVED")));
        }

        @Test
        @DisplayName("201 Created when ADMIN creates another ADMIN")
        void admin_can_create_admin() throws Exception {
            UserSummaryResponse created = new UserSummaryResponse(
                    43L, "new.user@x.com", "New", "User", Role.ADMIN, "default", true, null);
            when(service.createUser(any(), any())).thenReturn(created);

            mockMvc.perform(post(BASE)
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("ADMIN")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role", is("ADMIN")));
        }

        @Test
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_create_users() throws Exception {
            mockMvc.perform(post(BASE)
                            .with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("401 Unauthorized for anonymous request")
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("USER")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("400 Bad Request when role is missing")
        void rejects_missing_role() throws Exception {
            mockMvc.perform(post(BASE)
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"A\",\"lastname\":\"B\","
                                    + "\"email\":\"a@b.com\",\"password\":\"Password1!\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.role", is("Role is required.")));
        }

        @Test
        @DisplayName("400 Bad Request when role value is not a valid enum")
        void rejects_invalid_role_value() throws Exception {
            mockMvc.perform(post(BASE)
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"A\",\"lastname\":\"B\","
                                    + "\"email\":\"a@b.com\",\"password\":\"Password1!\","
                                    + "\"role\":\"SUPERADMIN\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.bad.request")));
        }

        @Test
        @DisplayName("409 Conflict when email already exists")
        void rejects_duplicate_email() throws Exception {
            when(service.createUser(any(), any())).thenThrow(new UserAlreadyExistsException(
                    "A user with email [new.user@x.com] already exists.",
                    "auth.user.already.exists"));

            mockMvc.perform(post(BASE)
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("USER")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode", is("auth.user.already.exists")));
        }
    }

    // -------------------------------------------------------
    // PATCH /api/v1/auth/users/{userId}
    // -------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auth/users/{userId}")
    class UpdateUser {

        @Test
        @DisplayName("200 OK when ADMIN edits a user's name")
        void admin_can_edit_user() throws Exception {
            UserSummaryResponse updated = new UserSummaryResponse(
                    2L, "user@x.com", "Renamed", "Smith", Role.USER, "t1", true, null);
            when(service.updateUser(any(), any(), any())).thenReturn(updated);

            mockMvc.perform(patch(BASE + "/2")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new AdminUpdateUserRequest("Renamed", null, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstname", is("Renamed")));
        }

        @Test
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_edit_users() throws Exception {
            mockMvc.perform(patch(BASE + "/2")
                            .with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new AdminUpdateUserRequest("X", null, null))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("400 Bad Request when body has no fields (service guard)")
        void rejects_empty_body() throws Exception {
            when(service.updateUser(any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("At least one field required"));

            mockMvc.perform(patch(BASE + "/2")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.bad.request")));
        }

        @Test
        @DisplayName("409 Conflict when new email is taken")
        void rejects_taken_email() throws Exception {
            when(service.updateUser(any(), any(), any())).thenThrow(new UserAlreadyExistsException(
                    "That email address is already in use by another account.",
                    "auth.account.email.taken"));

            mockMvc.perform(patch(BASE + "/2")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new AdminUpdateUserRequest(null, null, "taken@x.com"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode", is("auth.account.email.taken")));
        }
    }

    // -------------------------------------------------------
    // PATCH /api/v1/auth/users/{userId}/status
    // -------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auth/users/{userId}/status")
    class UpdateUserStatus {

        @Test
        @DisplayName("200 OK when ADMIN deactivates a user")
        void admin_can_deactivate_user() throws Exception {
            UserSummaryResponse disabled = new UserSummaryResponse(
                    2L, "user@x.com", "Bob", "Smith", Role.USER, "t1", false, null);
            when(service.setUserStatus(any(), anyLong(), anyLong(), anyBoolean()))
                    .thenReturn(disabled);

            mockMvc.perform(patch(BASE + "/2/status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateUserStatusRequest(false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountEnabled", is(false)));
        }

        @Test
        @DisplayName("400 Bad Request when ADMIN deactivates self")
        void admin_cannot_deactivate_self() throws Exception {
            when(service.setUserStatus(any(), anyLong(), anyLong(), anyBoolean()))
                    .thenThrow(new AdminActionNotAllowedException(
                            "Admins cannot deactivate their own account.",
                            "auth.admin.self.deactivate.denied"));

            mockMvc.perform(patch(BASE + "/1/status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateUserStatusRequest(false))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.admin.self.deactivate.denied")));
        }

        @Test
        @DisplayName("400 Bad Request when enabled flag is missing")
        void rejects_missing_enabled_flag() throws Exception {
            mockMvc.perform(patch(BASE + "/2/status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.enabled", is("Enabled flag is required.")));
        }

        @Test
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_change_status() throws Exception {
            mockMvc.perform(patch(BASE + "/2/status")
                            .with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateUserStatusRequest(false))))
                    .andExpect(status().isForbidden());
        }
    }

    // -------------------------------------------------------
    // PATCH /api/v1/auth/users/{userId}/seller-status
    // -------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auth/users/{userId}/seller-status")
    class UpdateSellerStatus {

        @Test
        @DisplayName("200 OK when ADMIN approves a pending seller")
        void admin_can_approve_seller() throws Exception {
            UserSummaryResponse approved = new UserSummaryResponse(
                    2L, "seller@x.com", "Sella", "Vendor", Role.SELLER, "default", true,
                    SellerStatus.APPROVED);
            when(service.setSellerStatus(any(), anyLong(), any())).thenReturn(approved);

            mockMvc.perform(patch(BASE + "/2/seller-status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateSellerStatusRequest(SellerStatus.APPROVED))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerStatus", is("APPROVED")));

            verify(service).setSellerStatus("admin@x.com", 2L, SellerStatus.APPROVED);
        }

        @Test
        @DisplayName("200 OK when ADMIN suspends a seller")
        void admin_can_suspend_seller() throws Exception {
            UserSummaryResponse suspended = new UserSummaryResponse(
                    2L, "seller@x.com", "Sella", "Vendor", Role.SELLER, "default", true,
                    SellerStatus.SUSPENDED);
            when(service.setSellerStatus(any(), anyLong(), any())).thenReturn(suspended);

            mockMvc.perform(patch(BASE + "/2/seller-status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateSellerStatusRequest(SellerStatus.SUSPENDED))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerStatus", is("SUSPENDED")));
        }

        @Test
        @DisplayName("400 Bad Request when target is not a SELLER")
        void rejects_non_seller_target() throws Exception {
            when(service.setSellerStatus(any(), anyLong(), any()))
                    .thenThrow(new AdminActionNotAllowedException(
                            "User [3] is not a SELLER — seller status does not apply.",
                            "auth.seller.status.not.seller"));

            mockMvc.perform(patch(BASE + "/3/seller-status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateSellerStatusRequest(SellerStatus.APPROVED))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.seller.status.not.seller")));
        }

        @Test
        @DisplayName("400 Bad Request when status is missing")
        void rejects_missing_status() throws Exception {
            mockMvc.perform(patch(BASE + "/2/seller-status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.status", is("Seller status is required.")));
        }

        @Test
        @DisplayName("400 Bad Request when status value is not a valid enum")
        void rejects_invalid_status_value() throws Exception {
            mockMvc.perform(patch(BASE + "/2/seller-status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"BANNED\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.bad.request")));
        }

        @Test
        @DisplayName("404 Not Found when target user does not exist")
        void returns_404_for_missing_user() throws Exception {
            when(service.setSellerStatus(any(), anyLong(), any()))
                    .thenThrow(new AuthUserNotFoundException(
                            "User 99 not found", "auth.user.not.found"));

            mockMvc.perform(patch(BASE + "/99/seller-status")
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateSellerStatusRequest(SellerStatus.APPROVED))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_change_seller_status() throws Exception {
            mockMvc.perform(patch(BASE + "/2/seller-status")
                            .with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateSellerStatusRequest(SellerStatus.APPROVED))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("401 Unauthorized for anonymous request")
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(patch(BASE + "/2/seller-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateSellerStatusRequest(SellerStatus.APPROVED))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -------------------------------------------------------
    // DELETE /api/v1/auth/users/{userId}
    // -------------------------------------------------------
    @Nested
    @DisplayName("DELETE /api/v1/auth/users/{userId}")
    class DeleteUser {

        @Test
        @DisplayName("204 No Content when ADMIN deletes a user")
        void admin_can_delete_user() throws Exception {
            mockMvc.perform(delete(BASE + "/2").with(user(adminPrincipal)))
                    .andExpect(status().isNoContent());

            verify(service).deleteUser("admin@x.com", 1L, 2L);
        }

        @Test
        @DisplayName("400 Bad Request when ADMIN deletes self")
        void admin_cannot_delete_self() throws Exception {
            doThrow(new AdminActionNotAllowedException(
                    "Admins cannot delete their own account.",
                    "auth.admin.self.delete.denied"))
                    .when(service).deleteUser(any(), anyLong(), anyLong());

            mockMvc.perform(delete(BASE + "/1").with(user(adminPrincipal)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.admin.self.delete.denied")));
        }

        @Test
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_delete_users() throws Exception {
            mockMvc.perform(delete(BASE + "/2").with(user(userPrincipal)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("401 Unauthorized for anonymous request")
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(delete(BASE + "/2"))
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
                    2L, "user@x.com", "Bob", "Smith", Role.SELLER, "t1", true,
                    SellerStatus.APPROVED);
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