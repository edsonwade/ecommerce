package code.with.vanilson.authentication.controller;

import code.with.vanilson.authentication.application.AccountResponse;
import code.with.vanilson.authentication.application.AccountService;
import code.with.vanilson.authentication.application.AccountUpdateResponse;
import code.with.vanilson.authentication.application.AuthResponse;
import code.with.vanilson.authentication.application.ChangePasswordRequest;
import code.with.vanilson.authentication.application.UpdateAccountRequest;
import code.with.vanilson.authentication.config.JwtAuthFilter;
import code.with.vanilson.authentication.config.SecurityConfig;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import code.with.vanilson.authentication.exception.InvalidAccountPasswordException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import code.with.vanilson.authentication.presentation.AccountController;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AccountController - WebMvc slice tests")
class AccountControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AccountService         service;
    @MockBean JwtService             jwtService;
    @MockBean TokenRepository        tokenRepository;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthFilter          jwtAuthFilter;

    private static final String BASE = "/api/v1/auth/account";

    private UserDetailsAdapter userPrincipal;
    private UserDetailsAdapter sellerPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(
                    inv.<ServletRequest>getArgument(0),
                    inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());

        userPrincipal = new UserDetailsAdapter(
                User.builder().id(7L).email("ana@x.com").role(Role.USER)
                        .tenantId("default").firstname("Ana").lastname("Silva")
                        .password("hashed").accountEnabled(true).build());
        sellerPrincipal = new UserDetailsAdapter(
                User.builder().id(8L).email("seller@x.com").role(Role.SELLER)
                        .tenantId("default").firstname("Sam").lastname("Seller")
                        .password("hashed").accountEnabled(true).build());
    }

    private AccountResponse account() {
        return new AccountResponse(7L, "Ana", "Silva", "ana@x.com", "USER",
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Nested @DisplayName("GET /me")
    class GetMe {
        @Test
        void returns_own_account() throws Exception {
            when(service.getAccount(7L)).thenReturn(account());
            mockMvc.perform(get(BASE + "/me").with(user(userPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email", is("ana@x.com")))
                    .andExpect(jsonPath("$.role", is("USER")));
        }

        @Test
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/me")).andExpect(status().isUnauthorized());
        }
    }

    @Nested @DisplayName("PATCH /me")
    class PatchMe {
        @Test
        void name_only_update_returns_account_without_tokens() throws Exception {
            when(service.updateAccount(eq(7L), any(UpdateAccountRequest.class)))
                    .thenReturn(new AccountUpdateResponse(account(), null));
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"Ana\",\"lastname\":\"Silva\",\"email\":\"ana@x.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account.email", is("ana@x.com")))
                    .andExpect(jsonPath("$.tokens").doesNotExist());
        }

        @Test
        void email_change_returns_fresh_token_pair() throws Exception {
            AuthResponse tokens = AuthResponse.of("a2", "r2", "7", "new@x.com", "USER", "default");
            when(service.updateAccount(eq(7L), any(UpdateAccountRequest.class)))
                    .thenReturn(new AccountUpdateResponse(
                            new AccountResponse(7L, "Ana", "Silva", "new@x.com", "USER",
                                    LocalDateTime.of(2026, 1, 1, 0, 0)), tokens));
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"Ana\",\"lastname\":\"Silva\","
                                    + "\"email\":\"new@x.com\",\"currentPassword\":\"Secret123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokens.accessToken", is("a2")))
                    .andExpect(jsonPath("$.account.email", is("new@x.com")));
        }

        @Test
        void taken_email_maps_to_409() throws Exception {
            when(service.updateAccount(eq(7L), any(UpdateAccountRequest.class)))
                    .thenThrow(new UserAlreadyExistsException(
                            "That email address is already in use by another account.",
                            "auth.account.email.taken"));
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"Ana\",\"lastname\":\"Silva\","
                                    + "\"email\":\"taken@x.com\",\"currentPassword\":\"Secret123!\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode", is("auth.account.email.taken")));
        }

        @Test
        void wrong_password_maps_to_400() throws Exception {
            when(service.updateAccount(eq(7L), any(UpdateAccountRequest.class)))
                    .thenThrow(new InvalidAccountPasswordException(
                            "The password you entered is incorrect.", "auth.account.password.invalid"));
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"Ana\",\"lastname\":\"Silva\","
                                    + "\"email\":\"new@x.com\",\"currentPassword\":\"wrong\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.account.password.invalid")));
        }

        @Test
        void blank_firstname_fails_bean_validation() throws Exception {
            mockMvc.perform(patch(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstname\":\"\",\"lastname\":\"Silva\",\"email\":\"ana@x.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.validation.failed")));
        }
    }

    @Nested @DisplayName("POST /change-password")
    class ChangePassword {
        @Test
        void valid_change_returns_fresh_pair() throws Exception {
            when(service.changePassword(eq(7L), any(ChangePasswordRequest.class)))
                    .thenReturn(AuthResponse.of("a3", "r3", "7", "ana@x.com", "USER", "default"));
            mockMvc.perform(post(BASE + "/change-password").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"OldPass123!\","
                                    + "\"newPassword\":\"NewPass123!\",\"confirmPassword\":\"NewPass123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", is("a3")));
        }

        @Test
        void short_new_password_fails_bean_validation() throws Exception {
            mockMvc.perform(post(BASE + "/change-password").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"OldPass123!\","
                                    + "\"newPassword\":\"short\",\"confirmPassword\":\"short\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("DELETE /me")
    class DeleteMe {
        @Test
        void user_role_deletes_own_account_204() throws Exception {
            mockMvc.perform(delete(BASE + "/me").with(user(userPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Secret123!\"}"))
                    .andExpect(status().isNoContent());
            verify(service).deleteAccount(7L, "Secret123!");
        }

        @Test
        void seller_role_gets_403() throws Exception {
            mockMvc.perform(delete(BASE + "/me").with(user(sellerPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Secret123!\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(delete(BASE + "/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"x\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
