package code.with.vanilson.authentication.bdd.steps;

import code.with.vanilson.authentication.config.AdminBootstrapRunner;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthStepDefinitions — step definitions for auth.feature.
 *
 * NOT annotated with @Component intentionally:
 *   cucumber-spring calls AutowireCapableBeanFactory.autowireBean() on each new
 *   instance it creates per scenario. @Component would make Spring treat this as a
 *   singleton, preventing @Value("${local.server.port}") from resolving correctly
 *   at construction time (the server port is only bound after the context starts).
 *
 * Port is injected via @Autowired Environment, resolved lazily inside the @Before
 *   hook — by which point the embedded server is guaranteed to be running.
 *
 * Unique-email strategy (duplicate-email test):
 *   uniqueEmail(seed) caches seed → unique address in an instance-local Map.
 *   Given "a user already exists with email X" and When "register with email X"
 *   both call uniqueEmail("X"), so they get the SAME address → real 409.
 */
public class AuthStepDefinitions {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());
    private static final String     BASE = "/api/v1/auth";

    /** Injected by cucumber-spring via autowireBean() — safe to use from @Before onward. */
    @Autowired
    private Environment environment;

    @Autowired
    private AdminBootstrapRunner adminBootstrapRunner;

    // ------------------------------------------------------------------
    // Per-scenario state — reset in @Before (new instance = clean state,
    // but explicit reset keeps the @Before readable and intent clear).
    // ------------------------------------------------------------------
    private Response            lastResponse;
    private String              currentAccessToken;
    private String              currentRefreshToken;
    private String              originalAccessToken;        // the FIRST token issued this scenario
    private Map<String, String> emailCache;                 // seed → unique address
    private Map<String, Object> lifecycleData;              // results of the full lifecycle When step

    @Before
    public void setUp() {
        int port = Integer.parseInt(environment.getProperty("local.server.port", "8080"));
        RestAssured.baseURI = "http://localhost";
        RestAssured.port    = port;

        lastResponse        = null;
        currentAccessToken  = null;
        currentRefreshToken = null;
        originalAccessToken = null;
        emailCache          = new HashMap<>();
        lifecycleData       = new HashMap<>();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Returns a stable unique e-mail for a seed within this scenario.
     * First call → creates and caches.  Subsequent calls → returns cached.
     * Critical: Given and When that reference the same seed get the SAME address.
     */
    private String uniqueEmail(String seed) {
        return emailCache.computeIfAbsent(
                seed,
                s -> s.replace("@", "_" + SEQ.incrementAndGet() + "@")
        );
    }

    private Response callRegister(String email, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(String.format(
                        "{\"firstname\":\"BDD\",\"lastname\":\"User\","
                        + "\"email\":\"%s\",\"password\":\"%s\"}",
                        email, password))
                .when()
                    .post(BASE + "/register")
                .then()
                    .extract().response();
    }

    private void captureTokens(Response r) {
        if (r.statusCode() == 200 || r.statusCode() == 201) {
            currentAccessToken  = r.jsonPath().getString("accessToken");
            currentRefreshToken = r.jsonPath().getString("refreshToken");
            if (originalAccessToken == null) {
                originalAccessToken = currentAccessToken;
            }
        }
    }

    // ------------------------------------------------------------------
    // Given
    // ------------------------------------------------------------------

    @Given("the authentication service is running")
    public void theAuthenticationServiceIsRunning() {
        int port = Integer.parseInt(environment.getProperty("local.server.port", "0"));
        assertThat(port).as("Embedded server port must be positive").isPositive();
    }

    @Given("no user exists with email {string}")
    public void noUserExistsWithEmail(String seed) {
        // Pre-seed the cache so any subsequent When step that references the same
        // seed resolves to the same unique address (and NOT a new collision).
        uniqueEmail(seed);
    }

    @Given("a user already exists with email {string}")
    public void aUserAlreadyExistsWithEmail(String seed) {
        String email = uniqueEmail(seed);   // cached — When step will reuse this exact address
        Response r = callRegister(email, "ExistPass1!");
        assertThat(r.statusCode())
                .as("Pre-registration must succeed (201). Got: %s", r.asString())
                .isEqualTo(201);
        captureTokens(r);
    }

    @Given("a user exists with email {string} and password {string}")
    public void aUserExistsWithEmailAndPassword(String seed, String password) {
        String email = uniqueEmail(seed);   // cached — login When step reuses this address
        Response r = callRegister(email, password);
        assertThat(r.statusCode())
                .as("Pre-registration must succeed (201). Got: %s", r.asString())
                .isEqualTo(201);
        captureTokens(r);
    }

    @Given("the admin account {string} has been seeded by AdminBootstrapRunner")
    public void theAdminAccountHasBeenSeeded(String adminEmail) throws Exception {
        // Pin the literal admin email in the cache so the login step does not uniquify it
        emailCache.put(adminEmail, adminEmail);
        // Idempotent: existsByEmail check prevents duplicate creation
        adminBootstrapRunner.run(null);
    }

    @Given("a user is registered and logged in with email {string}")
    public void aUserIsRegisteredAndLoggedIn(String seed) {
        String email = uniqueEmail(seed);
        Response r = callRegister(email, "BddPass1!");
        assertThat(r.statusCode())
                .as("Registration must succeed (201). Got: %s", r.asString())
                .isEqualTo(201);
        currentAccessToken  = r.jsonPath().getString("accessToken");
        currentRefreshToken = r.jsonPath().getString("refreshToken");
        originalAccessToken = currentAccessToken;
    }

    // ------------------------------------------------------------------
    // When
    // ------------------------------------------------------------------

    @When("I register with the following details:")
    public void iRegisterWithTheFollowingDetails(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        Map<String, String> data = rows.get(0);

        String seedEmail    = data.get("email");
        // uniqueEmail(seed) returns the cached address if a Given already registered it —
        // this is how the duplicate-email 409 scenario works correctly.
        String resolvedEmail = seedEmail != null ? uniqueEmail(seedEmail) : null;

        StringBuilder json = new StringBuilder("{");
        appendField(json, "firstname", data.get("firstname"));
        appendField(json, "lastname",  data.get("lastname"));
        if (resolvedEmail != null) appendField(json, "email", resolvedEmail);
        appendField(json, "password",  data.get("password"));
        appendField(json, "role",      data.get("role"));
        if (json.charAt(json.length() - 1) == ',') json.deleteCharAt(json.length() - 1);
        json.append("}");

        lastResponse = given()
                .contentType(ContentType.JSON)
                .body(json.toString())
                .when()
                    .post(BASE + "/register")
                .then()
                    .extract().response();

        captureTokens(lastResponse);
    }

    @When("I log in with email {string} and password {string}")
    public void iLogInWithEmailAndPassword(String seed, String password) {
        // uniqueEmail(seed) returns the same address the Given step registered.
        String email = uniqueEmail(seed);
        lastResponse = given()
                .contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                .when()
                    .post(BASE + "/login")
                .then()
                    .extract().response();

        if (lastResponse.statusCode() == 200) {
            currentAccessToken  = lastResponse.jsonPath().getString("accessToken");
            currentRefreshToken = lastResponse.jsonPath().getString("refreshToken");
        }
    }

    @When("I send an empty login request")
    public void iSendAnEmptyLoginRequest() {
        lastResponse = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                    .post(BASE + "/login")
                .then()
                    .extract().response();
    }

    @When("I refresh the token using the refresh token")
    public void iRefreshTheTokenUsingTheRefreshToken() {
        lastResponse = given()
                .header("Authorization", "Bearer " + currentRefreshToken)
                .when()
                    .post(BASE + "/refresh")
                .then()
                    .extract().response();

        if (lastResponse.statusCode() == 200) {
            currentAccessToken = lastResponse.jsonPath().getString("accessToken");
        }
    }

    @When("I refresh the token using the access token instead of the refresh token")
    public void iRefreshTheTokenUsingTheAccessTokenInsteadOfTheRefreshToken() {
        lastResponse = given()
                .header("Authorization", "Bearer " + currentAccessToken)
                .when()
                    .post(BASE + "/refresh")
                .then()
                    .extract().response();
    }

    @When("I call the refresh endpoint without any authorization header")
    public void iCallTheRefreshEndpointWithoutAnyAuthorizationHeader() {
        lastResponse = given()
                .when()
                    .post(BASE + "/refresh")
                .then()
                    .extract().response();
    }

    @When("I call the refresh endpoint with token {string}")
    public void iCallTheRefreshEndpointWithToken(String token) {
        lastResponse = given()
                .header("Authorization", "Bearer " + token)
                .when()
                    .post(BASE + "/refresh")
                .then()
                    .extract().response();
    }

    @When("I log out using the current access token")
    public void iLogOutUsingTheCurrentAccessToken() {
        lastResponse = given()
                .header("Authorization", "Bearer " + currentAccessToken)
                .when()
                    .post(BASE + "/logout")
                .then()
                    .extract().response();
    }

    @When("I call the logout endpoint without any authorization header")
    public void iCallTheLogoutEndpointWithoutAnyAuthorizationHeader() {
        lastResponse = given()
                .when()
                    .post(BASE + "/logout")
                .then()
                    .extract().response();
    }

    @When("I attempt to log out again with the same access token")
    public void iAttemptToLogOutAgainWithTheSameAccessToken() {
        lastResponse = given()
                .header("Authorization", "Bearer " + originalAccessToken)
                .when()
                    .post(BASE + "/logout")
                .then()
                    .extract().response();
    }

    @When("I complete the full auth lifecycle for {string}")
    public void iCompleteTheFullAuthLifecycleFor(String seed) {
        String email = uniqueEmail(seed);

        // Step 1 — Register
        Response reg = callRegister(email, "Lifecycle1!");
        assertThat(reg.statusCode()).as("lifecycle register").isEqualTo(201);
        String regRefreshToken = reg.jsonPath().getString("refreshToken");

        // Step 2 — Login (revokes all previous tokens, issues fresh pair)
        Response login = given()
                .contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"Lifecycle1!\"}", email))
                .when().post(BASE + "/login")
                .then().extract().response();
        assertThat(login.statusCode()).as("lifecycle login").isEqualTo(200);
        String loginAccessToken  = login.jsonPath().getString("accessToken");
        String loginRefreshToken = login.jsonPath().getString("refreshToken");

        // Step 3 — Refresh using the login refresh token
        Response refresh = given()
                .header("Authorization", "Bearer " + loginRefreshToken)
                .when().post(BASE + "/refresh")
                .then().extract().response();
        assertThat(refresh.statusCode()).as("lifecycle refresh").isEqualTo(200);
        String rotatedAccessToken = refresh.jsonPath().getString("accessToken");

        // Step 4 — Logout using the rotated access token
        Response logout = given()
                .header("Authorization", "Bearer " + rotatedAccessToken)
                .when().post(BASE + "/logout")
                .then().extract().response();
        assertThat(logout.statusCode()).as("lifecycle logout").isEqualTo(204);

        // Step 5 — Stale registration refresh token must now be rejected
        Response stale = given()
                .header("Authorization", "Bearer " + regRefreshToken)
                .when().post(BASE + "/refresh")
                .then().extract().response();

        // Persist results for Then assertions
        lifecycleData.put("logoutStatus",         logout.statusCode());
        lifecycleData.put("staleStatus",          stale.statusCode());
        lifecycleData.put("rotatedAccessToken",   rotatedAccessToken);
        lifecycleData.put("loginAccessToken",     loginAccessToken);

        lastResponse = logout;
    }

    // ------------------------------------------------------------------
    // Then / And
    // ------------------------------------------------------------------

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expected) {
        assertThat(lastResponse.statusCode())
                .as("Expected HTTP %d but was %d. Body: %s",
                        expected, lastResponse.statusCode(), lastResponse.asString())
                .isEqualTo(expected);
    }

    @And("the response contains a valid access token")
    public void theResponseContainsAValidAccessToken() {
        String token = lastResponse.jsonPath().getString("accessToken");
        assertThat(token)
                .as("accessToken must be a non-blank JWT (header.payload.signature)")
                .isNotBlank()
                .matches(".+\\..+\\..+");
    }

    @And("the response contains a valid refresh token")
    public void theResponseContainsAValidRefreshToken() {
        String token = lastResponse.jsonPath().getString("refreshToken");
        assertThat(token)
                .as("refreshToken must be a non-blank JWT (header.payload.signature)")
                .isNotBlank()
                .matches(".+\\..+\\..+");
    }

    @And("the response token type is {string}")
    public void theResponseTokenTypeIs(String expected) {
        assertThat(lastResponse.jsonPath().getString("tokenType")).isEqualTo(expected);
    }

    @And("the response role is {string}")
    public void theResponseRoleIs(String expected) {
        assertThat(lastResponse.jsonPath().getString("role")).isEqualTo(expected);
    }

    @And("the response email is {string}")
    public void theResponseEmailIs(String seed) {
        // The actual email was uniquified — match on the domain part only.
        String domain = seed.substring(seed.indexOf('@'));
        assertThat(lastResponse.jsonPath().getString("email"))
                .as("email domain should match '%s'", domain)
                .contains(domain);
    }

    @And("the error code is {string}")
    public void theErrorCodeIs(String expected) {
        assertThat(lastResponse.jsonPath().getString("errorCode"))
                .as("errorCode in response body")
                .isEqualTo(expected);
    }

    @And("the error message is {string}")
    public void theErrorMessageIs(String expected) {
        assertThat(lastResponse.jsonPath().getString("message")).isEqualTo(expected);
    }

    @And("the error message contains {string}")
    public void theErrorMessageContains(String expected) {
        assertThat(lastResponse.jsonPath().getString("message")).contains(expected);
    }

    @And("the field error {string} is present")
    public void theFieldErrorIsPresent(String field) {
        Object value = lastResponse.jsonPath().get("fieldErrors." + field);
        assertThat(value)
                .as("fieldErrors.%s must be present. Response: %s", field, lastResponse.asString())
                .isNotNull();
    }

    @And("the new access token is different from the original access token")
    public void theNewAccessTokenIsDifferentFromTheOriginalAccessToken() {
        assertThat(lastResponse.jsonPath().getString("accessToken"))
                .as("Refreshed access token must differ from the original token")
                .isNotEqualTo(originalAccessToken);
    }

    @And("the error response contains field {string}")
    public void theErrorResponseContainsField(String field) {
        assertThat((Object) lastResponse.jsonPath().get(field))
                .as("Error response must contain field '%s'. Response: %s",
                        field, lastResponse.asString())
                .isNotNull();
    }

    @Then("the lifecycle completes without errors")
    public void theLifecycleCompletesWithoutErrors() {
        assertThat(lifecycleData.get("logoutStatus"))
                .as("Lifecycle logout step should return 204")
                .isEqualTo(204);
    }

    @And("all tokens are properly rotated and invalidated")
    public void allTokensAreProperlyRotatedAndInvalidated() {
        assertThat(lifecycleData.get("staleStatus"))
                .as("Stale registration refresh token should be rejected (401)")
                .isEqualTo(401);
        assertThat(lifecycleData.get("rotatedAccessToken"))
                .as("Access token must have been rotated during the refresh step")
                .isNotEqualTo(lifecycleData.get("loginAccessToken"));
    }

    // ------------------------------------------------------------------
    // Private helper
    // ------------------------------------------------------------------
    private void appendField(StringBuilder sb, String key, String value) {
        if (value != null) {
            sb.append('"').append(key).append("\":\"").append(value).append("\",");
        }
    }
}
