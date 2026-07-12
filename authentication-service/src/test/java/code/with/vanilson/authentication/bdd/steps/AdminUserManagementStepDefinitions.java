package code.with.vanilson.authentication.bdd.steps;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdminUserManagementStepDefinitions — steps for admin-user-management.feature.
 * Same lifecycle rules as the other step classes: NOT @Component (cucumber-spring
 * autowires a fresh instance per scenario); port resolved lazily in @Before.
 * <p>
 * The admin actor is the user seeded by AdminBootstrapRunner
 * (defaults: admin@obsidian.com / Admin@123!).
 * </p>
 */
public class AdminUserManagementStepDefinitions {

    private static final AtomicLong SEQ         = new AtomicLong(System.nanoTime());
    private static final String     BASE        = "/api/v1/auth";
    private static final String     USERS       = BASE + "/users";
    private static final String     ADMIN_EMAIL = "admin@obsidian.com";
    private static final String     ADMIN_PASS  = "Admin@123!";

    @Autowired
    private Environment environment;

    private Response            lastResponse;
    private String              actorToken;
    private String              lastCreatedEmail;
    private Long                lastCreatedUserId;
    private Map<String, String> emailCache;

    @Before
    public void setUp() {
        int port = Integer.parseInt(environment.getProperty("local.server.port", "8080"));
        RestAssured.baseURI = "http://localhost";
        RestAssured.port    = port;
        lastResponse      = null;
        actorToken        = null;
        lastCreatedEmail  = null;
        lastCreatedUserId = null;
        emailCache        = new HashMap<>();
    }

    private String uniqueEmail(String seed) {
        return emailCache.computeIfAbsent(
                seed, s -> s.replace("@", "_" + SEQ.incrementAndGet() + "@"));
    }

    private Response login(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                .post(BASE + "/login").then().extract().response();
    }

    private Response createUser(String token, String email, String password, String role) {
        return given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body(String.format(
                        "{\"firstname\":\"Bdd\",\"lastname\":\"Created\","
                        + "\"email\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}",
                        email, password, role))
                .post(USERS).then().extract().response();
    }

    // ------------------------------------------------------------------

    @Given("the platform admin is logged in")
    public void thePlatformAdminIsLoggedIn() {
        Response r = login(ADMIN_EMAIL, ADMIN_PASS);
        assertThat(r.statusCode())
                .as("bootstrap admin must be able to log in")
                .isEqualTo(200);
        actorToken = r.jsonPath().getString("accessToken");
    }

    @Given("a registered regular user {string} with password {string}")
    public void aRegisteredRegularUser(String emailSeed, String password) {
        String email = uniqueEmail(emailSeed);
        Response r = given().contentType(ContentType.JSON)
                .body(String.format(
                        "{\"firstname\":\"Reg\",\"lastname\":\"Ular\","
                        + "\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                .post(BASE + "/register").then().extract().response();
        assertThat(r.statusCode()).isEqualTo(201);
        actorToken = r.jsonPath().getString("accessToken");
    }

    @When("the admin creates a user {string} with password {string} and role {string}")
    public void theAdminCreatesAUser(String emailSeed, String password, String role) {
        lastCreatedEmail = uniqueEmail(emailSeed);
        lastResponse = createUser(actorToken, lastCreatedEmail, password, role);
        if (lastResponse.statusCode() == 201) {
            lastCreatedUserId = lastResponse.jsonPath().getLong("id");
        }
    }

    @When("the admin renames that user to {string} {string}")
    public void theAdminRenamesThatUser(String firstname, String lastname) {
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + actorToken)
                .body(String.format(
                        "{\"firstname\":\"%s\",\"lastname\":\"%s\"}", firstname, lastname))
                .patch(USERS + "/" + lastCreatedUserId).then().extract().response();
    }

    @When("the admin sets that user's status to disabled")
    public void theAdminDisablesThatUser() {
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + actorToken)
                .body("{\"enabled\":false}")
                .patch(USERS + "/" + lastCreatedUserId + "/status").then().extract().response();
        assertThat(lastResponse.statusCode()).isEqualTo(200);
    }

    @When("the admin sets that user's status to enabled")
    public void theAdminEnablesThatUser() {
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + actorToken)
                .body("{\"enabled\":true}")
                .patch(USERS + "/" + lastCreatedUserId + "/status").then().extract().response();
        assertThat(lastResponse.statusCode()).isEqualTo(200);
    }

    @When("the admin deletes that user")
    public void theAdminDeletesThatUser() {
        lastResponse = given()
                .header("Authorization", "Bearer " + actorToken)
                .delete(USERS + "/" + lastCreatedUserId).then().extract().response();
        assertThat(lastResponse.statusCode()).isEqualTo(204);
    }

    @Then("the managed user response shows firstname {string} and lastname {string}")
    public void theManagedUserResponseShowsName(String firstname, String lastname) {
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        assertThat(lastResponse.jsonPath().getString("firstname")).isEqualTo(firstname);
        assertThat(lastResponse.jsonPath().getString("lastname")).isEqualTo(lastname);
    }

    @Then("login with the created email and password {string} fails with 401")
    public void loginWithTheCreatedEmailFails(String password) {
        Response r = login(lastCreatedEmail, password);
        assertThat(r.statusCode()).isEqualTo(401);
    }

    @And("the admin creates the same user again with password {string} and role {string}")
    public void theAdminCreatesTheSameUserAgain(String password, String role) {
        lastResponse = createUser(actorToken, lastCreatedEmail, password, role);
    }

    @When("that user tries to create a user {string} with password {string} and role {string}")
    public void thatUserTriesToCreateAUser(String emailSeed, String password, String role) {
        lastResponse = createUser(actorToken, uniqueEmail(emailSeed), password, role);
    }

    @When("someone registers publicly as {string} with password {string} and role {string}")
    public void someoneRegistersPubliclyWithRole(String emailSeed, String password, String role) {
        lastResponse = given().contentType(ContentType.JSON)
                .body(String.format(
                        "{\"firstname\":\"Pub\",\"lastname\":\"Lic\","
                        + "\"email\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}",
                        uniqueEmail(emailSeed), password, role))
                .post(BASE + "/register").then().extract().response();
    }

    @Then("the user creation succeeds with role {string}")
    public void theUserCreationSucceedsWithRole(String role) {
        assertThat(lastResponse.statusCode()).isEqualTo(201);
        assertThat(lastResponse.jsonPath().getString("role")).isEqualTo(role);
        assertThat(lastResponse.jsonPath().getString("email")).isEqualTo(lastCreatedEmail);
    }

    @And("login with the created email and password {string} succeeds with role {string}")
    public void loginWithTheCreatedEmailSucceeds(String password, String role) {
        Response r = login(lastCreatedEmail, password);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.jsonPath().getString("role")).isEqualTo(role);
    }

    @Then("the user creation fails with {int} and error code {string}")
    public void theUserCreationFailsWithErrorCode(int status, String errorCode) {
        assertThat(lastResponse.statusCode()).isEqualTo(status);
        assertThat(lastResponse.jsonPath().getString("errorCode")).isEqualTo(errorCode);
    }

    @Then("the user creation fails with {int}")
    public void theUserCreationFailsWith(int status) {
        assertThat(lastResponse.statusCode()).isEqualTo(status);
    }

    // ------------------------------------------------------------------
    // Seller approval flow (Fase 2)
    // ------------------------------------------------------------------

    @When("someone registers publicly as seller {string} with password {string}")
    public void someoneRegistersPubliclyAsSeller(String emailSeed, String password) {
        lastCreatedEmail = uniqueEmail(emailSeed);
        lastResponse = given().contentType(ContentType.JSON)
                .body(String.format(
                        "{\"firstname\":\"Pending\",\"lastname\":\"Seller\","
                        + "\"email\":\"%s\",\"password\":\"%s\",\"role\":\"SELLER\"}",
                        lastCreatedEmail, password))
                .post(BASE + "/register").then().extract().response();
        if (lastResponse.statusCode() == 201) {
            // /register returns userId as a string — keep it for follow-up admin actions.
            lastCreatedUserId = Long.parseLong(lastResponse.jsonPath().getString("userId"));
        }
    }

    @When("the admin sets that seller's status to {string}")
    public void theAdminSetsThatSellersStatusTo(String status) {
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + actorToken)
                .body(String.format("{\"status\":\"%s\"}", status))
                .patch(USERS + "/" + lastCreatedUserId + "/seller-status")
                .then().extract().response();
    }

    @Then("the seller status response shows {string}")
    public void theSellerStatusResponseShows(String sellerStatus) {
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        assertThat(lastResponse.jsonPath().getString("sellerStatus")).isEqualTo(sellerStatus);
    }

    @Then("the user creation shows seller status {string}")
    public void theUserCreationShowsSellerStatus(String sellerStatus) {
        assertThat(lastResponse.statusCode()).isEqualTo(201);
        assertThat(lastResponse.jsonPath().getString("sellerStatus")).isEqualTo(sellerStatus);
    }

    @Then("the registration response shows seller status {string}")
    public void theRegistrationResponseShowsSellerStatus(String sellerStatus) {
        assertThat(lastResponse.statusCode()).isEqualTo(201);
        assertThat(lastResponse.jsonPath().getString("sellerStatus")).isEqualTo(sellerStatus);
    }
}
