package code.with.vanilson.authentication.controller;

import code.with.vanilson.authentication.application.AuthResponse;
import code.with.vanilson.authentication.application.AuthService;
import code.with.vanilson.authentication.config.JwtAuthFilter;
import code.with.vanilson.authentication.config.SecurityConfig;
import code.with.vanilson.authentication.exception.InvalidCredentialsException;
import code.with.vanilson.authentication.exception.InvalidTokenException;
import code.with.vanilson.authentication.exception.RegistrationException;
import code.with.vanilson.authentication.exception.TokenRevokedException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import code.with.vanilson.authentication.presentation.AuthController;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthControllerTest — @WebMvcTest slice.
 * Validates HTTP contracts: status codes, response structure, header presence,
 * validation errors, and security enforcement — without hitting the database.
 *
 * <h3>JwtAuthFilter mock strategy</h3>
 * <p>Mockito 5.x (Spring Boot 3) uses the inline mock maker, which mocks {@code final}
 * methods. {@code OncePerRequestFilter.doFilter()} is {@code final}, so without setup the
 * mock is a no-op — the Spring Security {@code VirtualFilterChain} stalls before
 * {@code ExceptionTranslationFilter} and {@code AuthorizationFilter} can run.</p>
 *
 * <p>{@link #setUpFilterPassThrough()} configures {@code doFilter()} to forward every
 * request to the next filter in the chain, letting Spring Security enforce access
 * rules (permitAll for public endpoints, authenticated-only for protected ones).</p>
 *
 * <h3>SecurityFilterChain ordering</h3>
 * <p>{@code SecurityConfig.securityFilterChain()} is annotated {@code @Order(1)}, which
 * guarantees it is matched by {@code FilterChainProxy} before Spring Boot's default
 * HTTP-Basic chain ({@code @Order(Integer.MAX_VALUE - 5)}). Without that annotation both
 * chains may coexist in {@code @WebMvcTest} and the wrong one wins.</p>
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AuthController WebMvc Tests")
class AuthControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockBean AuthService            authService;
    @MockBean JwtService             jwtService;
    @MockBean TokenRepository        tokenRepository;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthFilter          jwtAuthFilter;

    private static final String BASE = "/api/v1/auth";

    private static final AuthResponse SAMPLE_RESPONSE = AuthResponse.of(
            "sample.access.token", "sample.refresh.token",
            "1", "user@example.com", "USER", "default");

    /**
     * Configure the JwtAuthFilter mock to forward every request to the next filter.
     * Without this, Mockito's inline mock maker stubs the {@code final doFilter()} as a
     * no-op, stopping the Spring Security VirtualFilterChain before security rules run.
     */
    @BeforeEach
    void setUpFilterPassThrough() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(
                    inv.<ServletRequest>getArgument(0),
                    inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
    }

    // -------------------------------------------------------
    // POST /register
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("201 Created with tokens on valid payload")
        void validRegisterReturns201() throws Exception {
            when(authService.register(any())).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Jane", "lastname", "Doe",
                            "email", "jane@example.com", "password", "password123")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken", is("sample.access.token")))
                    .andExpect(jsonPath("$.refreshToken", is("sample.refresh.token")))
                    .andExpect(jsonPath("$.tokenType", is("Bearer")))
                    .andExpect(jsonPath("$.email", is("user@example.com")))
                    .andExpect(jsonPath("$.role", is("USER")));
        }

        @Test
        @DisplayName("409 Conflict when email already registered")
        void duplicateEmailReturns409() throws Exception {
            doThrow(new UserAlreadyExistsException(
                    "A user with email [jane@example.com] already exists.",
                    "auth.user.already.exists"))
                    .when(authService).register(any());

            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Jane", "lastname", "Doe",
                            "email", "jane@example.com", "password", "password123")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status", is(409)))
                    .andExpect(jsonPath("$.errorCode", is("auth.user.already.exists")))
                    .andExpect(jsonPath("$.message", containsString("already exists")))
                    .andExpect(jsonPath("$.timestamp", notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request when email is missing")
        void missingEmailReturns400() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Jane", "lastname", "Doe",
                            "password", "password123")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errorCode", is("auth.validation.failed")))
                    .andExpect(jsonPath("$.fieldErrors", hasKey("email")));
        }

        @Test
        @DisplayName("400 Bad Request when email format is invalid")
        void invalidEmailFormatReturns400() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Jane", "lastname", "Doe",
                            "email", "not-an-email", "password", "password123")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email", notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request when password is too short (< 8 chars)")
        void shortPasswordReturns400() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Jane", "lastname", "Doe",
                            "email", "jane@example.com", "password", "short")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request when firstname is blank")
        void blankFirstnameReturns400() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "", "lastname", "Doe",
                            "email", "jane@example.com", "password", "password123")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.firstname", notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request when body is completely empty")
        void emptyBodyReturns400() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", notNullValue()));
        }

        @Test
        @DisplayName("201 Created with SELLER role when registering as seller")
        void sellerRegistrationReturns201WithSellerRole() throws Exception {
            AuthResponse sellerResponse = AuthResponse.of(
                    "seller.access.token", "seller.refresh.token",
                    "2", "seller@example.com", "SELLER", "default");
            when(authService.register(any())).thenReturn(sellerResponse);

            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Alice", "lastname", "Trader",
                            "email", "seller@example.com", "password", "password123",
                            "role", "SELLER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role", is("SELLER")))
                    .andExpect(jsonPath("$.accessToken", is("seller.access.token")));
        }

        @Test
        @DisplayName("400 Bad Request when self-registering as ADMIN")
        void adminSelfRegistrationReturns400() throws Exception {
            doThrow(new RegistrationException(
                    "Self-registration as ADMIN is not permitted.",
                    "auth.register.admin.denied"))
                    .when(authService).register(any());

            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Evil", "lastname", "Hacker",
                            "email", "evil@example.com", "password", "password123",
                            "role", "ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errorCode", is("auth.register.admin.denied")));
        }

        @Test
        @DisplayName("400 Bad Request when role value is invalid")
        void invalidRoleReturns400() throws Exception {
            doThrow(new RegistrationException(
                    "Registration failed: [SUPERADMIN] is not a valid role.",
                    "auth.register.invalid.role"))
                    .when(authService).register(any());

            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Dave", "lastname", "Jones",
                            "email", "dave@example.com", "password", "password123",
                            "role", "SUPERADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.register.invalid.role")));
        }
    }

    // -------------------------------------------------------
    // POST /login
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("200 OK with tokens on valid credentials")
        void validLoginReturns200() throws Exception {
            when(authService.login(any())).thenReturn(SAMPLE_RESPONSE);

            mockMvc.perform(post(BASE + "/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", "user@example.com", "password", "password123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", is("sample.access.token")))
                    .andExpect(jsonPath("$.refreshToken", is("sample.refresh.token")))
                    .andExpect(jsonPath("$.tokenType", is("Bearer")));
        }

        @Test
        @DisplayName("401 Unauthorized on wrong credentials")
        void wrongCredentialsReturns401() throws Exception {
            doThrow(new InvalidCredentialsException(
                    "Invalid email or password. Please check and try again.",
                    "auth.login.invalid.credentials"))
                    .when(authService).login(any());

            mockMvc.perform(post(BASE + "/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", "user@example.com", "password", "wrong")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.errorCode", is("auth.login.invalid.credentials")))
                    .andExpect(jsonPath("$.message",
                            is("Invalid email or password. Please check and try again.")));
        }

        @Test
        @DisplayName("400 Bad Request when email is missing from login body")
        void missingEmailInLoginReturns400() throws Exception {
            mockMvc.perform(post(BASE + "/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("password", "password123")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email", notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request when password is missing from login body")
        void missingPasswordInLoginReturns400() throws Exception {
            mockMvc.perform(post(BASE + "/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", "user@example.com")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
        }
    }

    // -------------------------------------------------------
    // POST /refresh
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /refresh")
    class Refresh {

        @Test
        @DisplayName("401 Unauthorized when no token is provided")
        void noTokenReturns401() throws Exception {
            // /refresh is permitAll() — Spring Security lets it through.
            // The service is responsible for rejecting requests without a Bearer header.
            doThrow(new InvalidTokenException(
                    "Invalid or malformed JWT token.", "auth.jwt.invalid"))
                    .when(authService).refreshToken(any());

            mockMvc.perform(post(BASE + "/refresh")
                    .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 OK with new token pair when valid Bearer token provided")
        @WithMockUser(username = "user@example.com", roles = "USER")
        void validTokenReturns200() throws Exception {
            when(authService.refreshToken(any())).thenReturn(
                    AuthResponse.of("newAccess", "newRefresh", "1", "user@example.com", "USER", "default"));

            mockMvc.perform(post(BASE + "/refresh")
                    .with(csrf())
                    .header("Authorization", "Bearer valid.refresh.jwt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", is("newAccess")))
                    .andExpect(jsonPath("$.refreshToken", is("newRefresh")));
        }

        @Test
        @DisplayName("401 Unauthorized when revoked token is used")
        @WithMockUser(username = "user@example.com", roles = "USER")
        void revokedTokenReturns401() throws Exception {
            doThrow(new TokenRevokedException(
                    "Refresh token is invalid or has been revoked. Please log in again.",
                    "auth.token.refresh.invalid"))
                    .when(authService).refreshToken(any());

            mockMvc.perform(post(BASE + "/refresh")
                    .with(csrf())
                    .header("Authorization", "Bearer revoked.refresh.jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode", is("auth.token.refresh.invalid")));
        }

        @Test
        @DisplayName("401 Unauthorized when access token used instead of refresh token")
        @WithMockUser(username = "user@example.com", roles = "USER")
        void accessTokenAsRefreshReturns401() throws Exception {
            doThrow(new InvalidTokenException(
                    "Invalid or malformed JWT token.",
                    "auth.jwt.invalid"))
                    .when(authService).refreshToken(any());

            mockMvc.perform(post(BASE + "/refresh")
                    .with(csrf())
                    .header("Authorization", "Bearer access.token.misused.as.refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode", is("auth.jwt.invalid")));
        }
    }

    // -------------------------------------------------------
    // POST /logout
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("401 Unauthorized when no token is provided")
        void noTokenReturns401() throws Exception {
            // /logout is a protected endpoint — Spring Security rejects the unauthenticated
            // request via the AuthenticationEntryPoint before it reaches the controller.
            mockMvc.perform(post(BASE + "/logout")
                    .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("204 No Content on successful logout with valid token")
        @WithMockUser(username = "user@example.com", roles = "USER")
        void validLogoutReturns204() throws Exception {
            doNothing().when(authService).logout(any());

            mockMvc.perform(post(BASE + "/logout")
                    .with(csrf())
                    .header("Authorization", "Bearer valid.access.jwt"))
                    .andExpect(status().isNoContent());
        }
    }

    // -------------------------------------------------------
    // Security headers
    // -------------------------------------------------------
    @Nested
    @DisplayName("Security Headers")
    class SecurityHeaders {

        @Test
        @DisplayName("401 responses include WWW-Authenticate header")
        void unauthorizedResponseHasWwwAuthenticate() throws Exception {
            // /logout is protected — entry point returns 401 + WWW-Authenticate header
            mockMvc.perform(post(BASE + "/logout")
                    .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("WWW-Authenticate"));
        }

        @Test
        @DisplayName("public endpoints are accessible without credentials")
        void publicEndpointsAccessibleWithoutCredentials() throws Exception {
            when(authService.login(any())).thenReturn(SAMPLE_RESPONSE);
            mockMvc.perform(post(BASE + "/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", "user@example.com", "password", "password123")))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // RBAC — @WithMockUser role simulation
    // -------------------------------------------------------
    @Nested
    @DisplayName("Role-Based Authorization")
    class RoleBasedAuthorization {

        @Test
        @DisplayName("USER role can call /logout")
        @WithMockUser(username = "user@example.com", roles = "USER")
        void userRoleCanCallLogout() throws Exception {
            doNothing().when(authService).logout(any());
            mockMvc.perform(post(BASE + "/logout")
                    .with(csrf())
                    .header("Authorization", "Bearer token"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("ADMIN role can call /logout")
        @WithMockUser(username = "admin@example.com", roles = "ADMIN")
        void adminRoleCanCallLogout() throws Exception {
            doNothing().when(authService).logout(any());
            mockMvc.perform(post(BASE + "/logout")
                    .with(csrf())
                    .header("Authorization", "Bearer token"))
                    .andExpect(status().isNoContent());
        }
    }

    // -------------------------------------------------------
    // Error response structure contract
    // -------------------------------------------------------
    @Nested
    @DisplayName("Error Response Contract")
    class ErrorResponseContract {

        @Test
        @DisplayName("error response contains timestamp, status, errorCode, message, path")
        void errorResponseContainsAllRequiredFields() throws Exception {
            doThrow(new InvalidCredentialsException(
                    "Invalid email or password. Please check and try again.",
                    "auth.login.invalid.credentials"))
                    .when(authService).login(any());

            mockMvc.perform(post(BASE + "/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", "u@example.com", "password", "wrong")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.timestamp", notNullValue()))
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.errorCode", notNullValue()))
                    .andExpect(jsonPath("$.message", notNullValue()))
                    .andExpect(jsonPath("$.path", notNullValue()));
        }

        @Test
        @DisplayName("validation error response contains fieldErrors map")
        void validationErrorContainsFieldErrors() throws Exception {
            mockMvc.perform(post(BASE + "/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", notNullValue()))
                    .andExpect(jsonPath("$.errorCode", is("auth.validation.failed")));
        }

        @Test
        @DisplayName("unsuccessful request leaves no side effects (server remains clean)")
        void unsuccessfulRequestLeavesNoSideEffects() throws Exception {
            // Sending invalid payload — no DB call should be made (no side effects)
            mockMvc.perform(post(BASE + "/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
            // AuthService.register was never called
            org.mockito.Mockito.verify(authService, org.mockito.Mockito.never()).register(any());
        }
    }

    // -------------------------------------------------------
    // Helper
    // -------------------------------------------------------
    private String json(String... kvPairs) throws Exception {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length - 1; i += 2) {
            map.put(kvPairs[i], kvPairs[i + 1]);
        }
        return objectMapper.writeValueAsString(map);
    }
}
