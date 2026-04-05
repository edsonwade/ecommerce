package code.with.vanilson.authentication.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthIntegrationTest — full-stack integration tests using:
 *   - @SpringBootTest: loads complete application context
 *   - @AutoConfigureMockMvc: provides MockMvc without a running server
 *   - Testcontainers: real PostgreSQL with Flyway migrations applied
 *   - @ActiveProfiles("test"): loads application-test.properties
 *
 * These tests cover end-to-end authentication flows with real DB persistence.
 * No mocking of services — exercises the full filter chain and security enforcement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Authentication Integration Tests (Testcontainers + MockMvc)")
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("auth_test")
                    .withUsername("auth_user")
                    .withPassword("auth_pass");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String BASE = "/api/v1/auth";

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------
    private String json(Object... pairs) throws Exception {
        var map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) map.put(pairs[i], pairs[i + 1]);
        return objectMapper.writeValueAsString(map);
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String uniqueEmail() {
        return "user_" + System.nanoTime() + "@example.com";
    }

    // -------------------------------------------------------
    // Registration
    // -------------------------------------------------------
    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("201 Created: new user returns access + refresh tokens")
        void registerNewUserReturns201WithTokens() throws Exception {
            String email = uniqueEmail();
            mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "John", "lastname", "Smith",
                            "email", email, "password", "securePass1")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken",  notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()))
                    .andExpect(jsonPath("$.tokenType",    is("Bearer")))
                    .andExpect(jsonPath("$.email",        is(email)))
                    .andExpect(jsonPath("$.role",         is("USER")));
        }

        @Test
        @DisplayName("409 Conflict: duplicate email rejected")
        void duplicateEmailReturns409() throws Exception {
            String email = uniqueEmail();
            String payload = json("firstname", "John", "lastname", "Smith",
                    "email", email, "password", "securePass1");

            mockMvc.perform(post(BASE + "/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(BASE + "/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode", is("auth.user.already.exists")))
                    .andExpect(jsonPath("$.message",   notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request: missing required fields")
        void missingFieldsReturn400() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request: password shorter than 8 characters")
        void shortPasswordReturn400() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", uniqueEmail(), "password", "short")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request: invalid email format")
        void invalidEmailFormatReturn400() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", "not-an-email", "password", "securePass1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email", notNullValue()));
        }
    }

    // -------------------------------------------------------
    // Login
    // -------------------------------------------------------
    @Nested
    @DisplayName("Login")
    class LoginFlow {

        @Test
        @DisplayName("200 OK: valid credentials return tokens")
        void validLoginReturns200() throws Exception {
            String email = uniqueEmail();
            mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", email, "password", "testPass99")))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(BASE + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", email, "password", "testPass99")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken",  notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()))
                    .andExpect(jsonPath("$.tokenType",    is("Bearer")));
        }

        @Test
        @DisplayName("401 Unauthorized: wrong password rejected with correct errorCode")
        void wrongPasswordReturns401() throws Exception {
            String email = uniqueEmail();
            mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", email, "password", "correctPass1")))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(BASE + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", email, "password", "wrongPassword")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode", is("auth.login.invalid.credentials")))
                    .andExpect(jsonPath("$.message",
                            is("Invalid email or password. Please check and try again.")));
        }

        @Test
        @DisplayName("401 Unauthorized: non-existent user rejected")
        void nonExistentUserReturns401() throws Exception {
            mockMvc.perform(post(BASE + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", "ghost_" + System.nanoTime() + "@example.com",
                            "password", "anyPassword1")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode", is("auth.login.invalid.credentials")));
        }
    }

    // -------------------------------------------------------
    // Token Refresh
    // -------------------------------------------------------
    @Nested
    @DisplayName("Token Refresh")
    class TokenRefresh {

        @Test
        @DisplayName("200 OK: valid refresh token returns new token pair")
        void validRefreshReturns200() throws Exception {
            String email = uniqueEmail();
            MvcResult registerResult = mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", email, "password", "testPass99")))
                    .andExpect(status().isCreated())
                    .andReturn();

            String refreshToken = bodyOf(registerResult).get("refreshToken").asText();

            mockMvc.perform(post(BASE + "/refresh")
                    .header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken",  notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()));
        }

        @Test
        @DisplayName("401 Unauthorized: access token used as refresh token is rejected")
        void accessTokenAsRefreshReturns401() throws Exception {
            String email = uniqueEmail();
            MvcResult reg = mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", email, "password", "testPass99")))
                    .andExpect(status().isCreated())
                    .andReturn();

            String accessToken = bodyOf(reg).get("accessToken").asText();

            mockMvc.perform(post(BASE + "/refresh")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("401 Unauthorized: missing Authorization header on /refresh")
        void missingHeaderOnRefreshReturns401() throws Exception {
            mockMvc.perform(post(BASE + "/refresh"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("401 Unauthorized: completely malformed token on /refresh")
        void malformedTokenOnRefreshReturns401() throws Exception {
            // JwtAuthFilter will intercept the malformed token and return 401 JSON
            mockMvc.perform(post(BASE + "/refresh")
                    .header("Authorization", "Bearer this.is.garbage"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -------------------------------------------------------
    // Logout
    // -------------------------------------------------------
    @Nested
    @DisplayName("Logout")
    class LogoutFlow {

        @Test
        @DisplayName("204 No Content: authenticated user can log out")
        void authenticatedUserCanLogout() throws Exception {
            String email = uniqueEmail();
            MvcResult reg = mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", email, "password", "testPass99")))
                    .andExpect(status().isCreated())
                    .andReturn();

            String accessToken = bodyOf(reg).get("accessToken").asText();

            mockMvc.perform(post(BASE + "/logout")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("401 Unauthorized: /logout requires authentication")
        void unauthenticatedLogoutReturns401() throws Exception {
            mockMvc.perform(post(BASE + "/logout"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("token is revoked after logout — subsequent use rejected")
        void tokenRevokedAfterLogout() throws Exception {
            String email = uniqueEmail();
            MvcResult reg = mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", email, "password", "testPass99")))
                    .andExpect(status().isCreated())
                    .andReturn();

            String accessToken = bodyOf(reg).get("accessToken").asText();

            // Logout — revoke all tokens
            mockMvc.perform(post(BASE + "/logout")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            // Reuse the old access token — must be rejected (token revoked in DB)
            // JwtAuthFilter checks DB: token is revoked → no authentication set → 401
            mockMvc.perform(post(BASE + "/logout")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -------------------------------------------------------
    // Protected endpoint enforcement
    // -------------------------------------------------------
    @Nested
    @DisplayName("Protected endpoint enforcement")
    class ProtectedEndpoints {

        @Test
        @DisplayName("unauthenticated request to /refresh returns 401")
        void unauthenticatedRefreshReturns401() throws Exception {
            mockMvc.perform(post(BASE + "/refresh"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("valid access token grants access to protected /logout endpoint")
        void validAccessTokenGrantsAccess() throws Exception {
            String email = uniqueEmail();
            MvcResult reg = mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "A", "lastname", "B",
                            "email", email, "password", "mySecurePass1")))
                    .andExpect(status().isCreated())
                    .andReturn();

            String accessToken = bodyOf(reg).get("accessToken").asText();

            mockMvc.perform(post(BASE + "/logout")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }
    }

    // -------------------------------------------------------
    // Full end-to-end flow
    // -------------------------------------------------------
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Full Auth Lifecycle: register → login → refresh → logout")
    class FullAuthLifecycle {

        @Test
        @Order(1)
        @DisplayName("full auth lifecycle completes without errors")
        void fullLifecycle() throws Exception {
            String email = uniqueEmail();

            // 1. Register
            MvcResult registerResult = mockMvc.perform(post(BASE + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("firstname", "Life", "lastname", "Cycle",
                            "email", email, "password", "lifecycle1")))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode reg = bodyOf(registerResult);
            String accessToken  = reg.get("accessToken").asText();
            String refreshToken = reg.get("refreshToken").asText();
            assertThat(accessToken).isNotBlank();
            assertThat(refreshToken).isNotBlank();

            // 2. Login (revokes old tokens, issues new pair)
            MvcResult loginResult = mockMvc.perform(post(BASE + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", email, "password", "lifecycle1")))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode login = bodyOf(loginResult);
            String loginAccessToken  = login.get("accessToken").asText();
            String loginRefreshToken = login.get("refreshToken").asText();
            assertThat(loginAccessToken).isNotBlank();

            // 3. Refresh using the refresh token from login
            MvcResult refreshResult = mockMvc.perform(post(BASE + "/refresh")
                    .header("Authorization", "Bearer " + loginRefreshToken))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode refresh = bodyOf(refreshResult);
            String newAccessToken = refresh.get("accessToken").asText();
            assertThat(newAccessToken).isNotBlank();
            assertThat(newAccessToken).isNotEqualTo(loginAccessToken); // rotated

            // 4. Logout using the newest access token
            mockMvc.perform(post(BASE + "/logout")
                    .header("Authorization", "Bearer " + newAccessToken))
                    .andExpect(status().isNoContent());

            // 5. Old access token from step 1 must now be rejected (all revoked on login)
            mockMvc.perform(post(BASE + "/logout")
                    .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }
    }
}
