package code.with.vanilson.authentication.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * AuthRestAssuredIT — API-level integration tests using REST Assured.
 *
 * Uses @SpringBootTest(webEnvironment = RANDOM_PORT) to start a real HTTP server
 * and Testcontainers for a real PostgreSQL database with Flyway migrations.
 *
 * These tests validate the full HTTP contract from client perspective:
 * status codes, JSON body structure, security headers, and response field values.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:"
        }
)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Authentication REST Assured Integration Tests")
class AuthRestAssuredIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("auth_it")
                    .withUsername("it_user")
                    .withPassword("it_pass");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @LocalServerPort
    int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port    = port;
        RestAssured.baseURI = "http://localhost";
    }

    private static final String BASE = "/api/v1/auth";

    private String uniqueEmail() {
        return "ra_user_" + System.nanoTime() + "@example.com";
    }

    // -------------------------------------------------------
    // Registration
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /register — REST Assured")
    class RegisterRA {

        @Test
        @DisplayName("201 Created with complete AuthResponse body")
        void returns201WithAuthResponse() {
            String email = uniqueEmail();

            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"firstname":"John","lastname":"Doe",
                       "email":"%s","password":"securePass1"}
                      """.formatted(email))
            .when()
                .post(BASE + "/register")
            .then()
                .statusCode(201)
                .body("accessToken",  notNullValue())
                .body("refreshToken", notNullValue())
                .body("tokenType",    equalTo("Bearer"))
                .body("email",        equalTo(email))
                .body("role",         equalTo("USER"))
                .body("userId",       notNullValue());
        }

        @Test
        @DisplayName("409 Conflict on duplicate email — errorCode matches messages.properties key")
        void returns409OnDuplicateEmail() {
            String email = uniqueEmail();
            String payload = """
                    {"firstname":"John","lastname":"Doe",
                     "email":"%s","password":"securePass1"}
                    """.formatted(email);

            given().contentType(ContentType.JSON).body(payload)
                    .when().post(BASE + "/register").then().statusCode(201);

            given()
                .contentType(ContentType.JSON)
                .body(payload)
            .when()
                .post(BASE + "/register")
            .then()
                .statusCode(409)
                .body("errorCode", equalTo("auth.user.already.exists"))
                .body("status",    equalTo(409))
                .body("timestamp", notNullValue())
                .body("path",      notNullValue());
        }

        @Test
        @DisplayName("400 Bad Request: all required fields missing → fieldErrors populated")
        void returns400WithFieldErrors() {
            given()
                .contentType(ContentType.JSON)
                .body("{}")
            .when()
                .post(BASE + "/register")
            .then()
                .statusCode(400)
                .body("errorCode",   equalTo("auth.validation.failed"))
                .body("fieldErrors", notNullValue());
        }

        @Test
        @DisplayName("400 Bad Request: invalid email format")
        void returns400OnInvalidEmail() {
            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"firstname":"A","lastname":"B",
                       "email":"not-an-email","password":"securePass1"}
                      """)
            .when()
                .post(BASE + "/register")
            .then()
                .statusCode(400)
                .body("fieldErrors.email", notNullValue());
        }

        @Test
        @DisplayName("400 Bad Request: password shorter than 8 chars")
        void returns400OnShortPassword() {
            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"firstname":"A","lastname":"B",
                       "email":"%s","password":"short"}
                      """.formatted(uniqueEmail()))
            .when()
                .post(BASE + "/register")
            .then()
                .statusCode(400)
                .body("fieldErrors.password", notNullValue());
        }
    }

    // -------------------------------------------------------
    // Login
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /login — REST Assured")
    class LoginRA {

        @Test
        @DisplayName("200 OK: correct credentials return token pair")
        void returns200OnValidCredentials() {
            String email = uniqueEmail();
            registerUser(email, "myPass123");

            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"email":"%s","password":"myPass123"}
                      """.formatted(email))
            .when()
                .post(BASE + "/login")
            .then()
                .statusCode(200)
                .body("accessToken",  notNullValue())
                .body("refreshToken", notNullValue())
                .body("tokenType",    equalTo("Bearer"));
        }

        @Test
        @DisplayName("401 Unauthorized: wrong password — correct message from messages.properties")
        void returns401OnWrongPassword() {
            var email = uniqueEmail();
            registerUser(email, "correctPass1");

            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"email":"%s","password":"wrongPassword"}
                      """.formatted(email))
            .when()
                .post(BASE + "/login")
            .then()
                .statusCode(401)
                .body("errorCode", equalTo("auth.login.invalid.credentials"))
                .body("message",   equalTo("Invalid email or password. Please check and try again."));
        }

        @Test
        @DisplayName("401 Unauthorized: unknown email")
        void returns401OnUnknownEmail() {
            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"email":"ghost_%d@example.com","password":"anyPassword"}
                      """.formatted(System.nanoTime()))
            .when()
                .post(BASE + "/login")
            .then()
                .statusCode(401)
                .body("errorCode", equalTo("auth.login.invalid.credentials"));
        }

        @Test
        @DisplayName("400 Bad Request: blank email")
        void returns400OnBlankEmail() {
            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"email":"","password":"somePass1"}
                      """)
            .when()
                .post(BASE + "/login")
            .then()
                .statusCode(400)
                .body("fieldErrors.email", notNullValue());
        }
    }

    // -------------------------------------------------------
    // Refresh
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /refresh — REST Assured")
    class RefreshRA {

        @Test
        @DisplayName("200 OK: valid refresh token returns new token pair")
        void returns200WithNewTokenPair() {
            String email = uniqueEmail();
            Response reg = registerUser(email, "refPass123");
            String refreshToken = reg.jsonPath().getString("refreshToken");

            given()
                .header("Authorization", "Bearer " + refreshToken)
            .when()
                .post(BASE + "/refresh")
            .then()
                .statusCode(200)
                .body("accessToken",  notNullValue())
                .body("refreshToken", notNullValue())
                .body("tokenType",    equalTo("Bearer"));
        }

        @Test
        @DisplayName("401 Unauthorized: access token misused as refresh token")
        void returns401WhenAccessTokenUsedAsRefresh() {
            String email = uniqueEmail();
            Response reg = registerUser(email, "refPass123");
            String accessToken = reg.jsonPath().getString("accessToken");

            given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .post(BASE + "/refresh")
            .then()
                .statusCode(401)
                .body("errorCode", notNullValue());
        }

        @Test
        @DisplayName("401 Unauthorized: revoked refresh token after logout")
        void returns401OnRevokedRefreshToken() {
            String email = uniqueEmail();
            Response reg = registerUser(email, "refPass123");
            String accessToken  = reg.jsonPath().getString("accessToken");
            String refreshToken = reg.jsonPath().getString("refreshToken");

            // Logout — revokes ALL tokens
            given()
                .header("Authorization", "Bearer " + accessToken)
                .when().post(BASE + "/logout").then().statusCode(204);

            // Try to use the refresh token — must fail
            given()
                .header("Authorization", "Bearer " + refreshToken)
            .when()
                .post(BASE + "/refresh")
            .then()
                .statusCode(401)
                .body("errorCode", equalTo("auth.token.refresh.invalid"));
        }

        @Test
        @DisplayName("401 Unauthorized: completely malformed token string")
        void returns401OnGarbageToken() {
            given()
                .header("Authorization", "Bearer garbage.token.here")
            .when()
                .post(BASE + "/refresh")
            .then()
                .statusCode(401)
                .body("errorCode", equalTo("auth.jwt.invalid"));
        }

        @Test
        @DisplayName("401 Unauthorized: no Authorization header")
        void returns401WithNoHeader() {
            given()
            .when()
                .post(BASE + "/refresh")
            .then()
                .statusCode(401);
        }
    }

    // -------------------------------------------------------
    // Logout
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /logout — REST Assured")
    class LogoutRA {

        @Test
        @DisplayName("204 No Content: authenticated user can log out")
        void returns204OnValidLogout() {
            String email = uniqueEmail();
            Response reg = registerUser(email, "logoutPass1");
            String accessToken = reg.jsonPath().getString("accessToken");

            given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .post(BASE + "/logout")
            .then()
                .statusCode(204);
        }

        @Test
        @DisplayName("401 Unauthorized: logout without token")
        void returns401WithoutToken() {
            given()
            .when()
                .post(BASE + "/logout")
            .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("token is invalidated after logout — reuse returns 401")
        void tokenInvalidatedAfterLogout() {
            String email = uniqueEmail();
            Response reg = registerUser(email, "logoutPass1");
            String accessToken = reg.jsonPath().getString("accessToken");

            given().header("Authorization", "Bearer " + accessToken)
                    .when().post(BASE + "/logout").then().statusCode(204);

            given().header("Authorization", "Bearer " + accessToken)
                    .when().post(BASE + "/logout").then().statusCode(401);
        }
    }

    // -------------------------------------------------------
    // Security headers & response contract
    // -------------------------------------------------------
    @Nested
    @DisplayName("Security Headers & Response Contract")
    class SecurityHeadersRA {

        @Test
        @DisplayName("401 response includes WWW-Authenticate header")
        void unauthorizedResponseHasWwwAuthenticateHeader() {
            given()
            .when()
                .post(BASE + "/logout")
            .then()
                .statusCode(401)
                .header("WWW-Authenticate", notNullValue());
        }

        @Test
        @DisplayName("error response has all required contract fields")
        void errorResponseHasRequiredFields() {
            Response resp = given()
                .contentType(ContentType.JSON)
                .body("""
                      {"email":"u@e.com","password":"wrongPass"}
                      """)
            .when()
                .post(BASE + "/login")
            .then()
                .statusCode(401)
                .extract().response();

            assertThat(resp.jsonPath().getString("timestamp")).isNotBlank();
            assertThat(resp.jsonPath().getInt("status")).isEqualTo(401);
            assertThat(resp.jsonPath().getString("errorCode")).isNotBlank();
            assertThat(resp.jsonPath().getString("message")).isNotBlank();
            assertThat(resp.jsonPath().getString("path")).isNotBlank();
        }

        @Test
        @DisplayName("public /register endpoint accessible without auth")
        void publicRegisterAccessibleWithoutAuth() {
            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"firstname":"A","lastname":"B",
                       "email":"%s","password":"pubPass12"}
                      """.formatted(uniqueEmail()))
            .when()
                .post(BASE + "/register")
            .then()
                .statusCode(201);
        }

        @Test
        @DisplayName("public /login endpoint accessible without auth")
        void publicLoginAccessibleWithoutAuth() {
            String email = uniqueEmail();
            registerUser(email, "pubPass12");

            given()
                .contentType(ContentType.JSON)
                .body("""
                      {"email":"%s","password":"pubPass12"}
                      """.formatted(email))
            .when()
                .post(BASE + "/login")
            .then()
                .statusCode(200);
        }
    }

    // -------------------------------------------------------
    // Full lifecycle (end-to-end)
    // -------------------------------------------------------
    @Test
    @DisplayName("Full lifecycle: register → login → refresh → logout → rejected reuse")
    void fullAuthLifecycle() {
        String email = uniqueEmail();

        // 1. Register
        Response reg = registerUser(email, "lifecycle1");
        String regRefreshToken = reg.jsonPath().getString("refreshToken");

        // 2. Login (rotates all tokens)
        Response login = given()
            .contentType(ContentType.JSON)
            .body("""
                  {"email":"%s","password":"lifecycle1"}
                  """.formatted(email))
            .when().post(BASE + "/login")
            .then().statusCode(200).extract().response();

        String loginAccess  = login.jsonPath().getString("accessToken");
        String loginRefresh = login.jsonPath().getString("refreshToken");

        // 3. Refresh with login's refresh token
        Response refresh = given()
            .header("Authorization", "Bearer " + loginRefresh)
            .when().post(BASE + "/refresh")
            .then().statusCode(200).extract().response();

        String newAccess = refresh.jsonPath().getString("accessToken");
        assertThat(newAccess).isNotEqualTo(loginAccess); // token was rotated

        // 4. Logout
        given().header("Authorization", "Bearer " + newAccess)
                .when().post(BASE + "/logout").then().statusCode(204);

        // 5. Old register refresh token is stale — must be rejected
        given().header("Authorization", "Bearer " + regRefreshToken)
                .when().post(BASE + "/refresh").then().statusCode(401);
    }

    // -------------------------------------------------------
    // Helper
    // -------------------------------------------------------
    private Response registerUser(String email, String password) {
        return given()
            .contentType(ContentType.JSON)
            .body("""
                  {"firstname":"Test","lastname":"User",
                   "email":"%s","password":"%s"}
                  """.formatted(email, password))
            .when()
                .post(BASE + "/register")
            .then()
                .statusCode(201)
                .extract().response();
    }
}
