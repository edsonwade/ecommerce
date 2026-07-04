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
 * AccountStepDefinitions — steps for account-settings.feature.
 * Same lifecycle rules as AuthStepDefinitions: NOT @Component (cucumber-spring autowires
 * a fresh instance per scenario); port resolved lazily in @Before.
 */
public class AccountStepDefinitions {

    private static final AtomicLong SEQ  = new AtomicLong(System.nanoTime());
    private static final String     BASE = "/api/v1/auth";

    @Autowired
    private Environment environment;

    private Response            lastResponse;
    private String              accessToken;
    private Map<String, String> emailCache;

    @Before
    public void setUp() {
        int port = Integer.parseInt(environment.getProperty("local.server.port", "8080"));
        RestAssured.baseURI = "http://localhost";
        RestAssured.port    = port;
        lastResponse = null;
        accessToken  = null;
        emailCache   = new HashMap<>();
    }

    private String uniqueEmail(String seed) {
        return emailCache.computeIfAbsent(
                seed, s -> s.replace("@", "_" + SEQ.incrementAndGet() + "@"));
    }

    private Response register(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(String.format(
                        "{\"firstname\":\"Acc\",\"lastname\":\"Bdd\","
                        + "\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                .post(BASE + "/register").then().extract().response();
    }

    private Response login(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                .post(BASE + "/login").then().extract().response();
    }

    // ------------------------------------------------------------------

    @Given("a registered user {string} with password {string}")
    public void aRegisteredUser(String emailSeed, String password) {
        Response r = register(uniqueEmail(emailSeed), password);
        assertThat(r.statusCode()).isEqualTo(201);
        accessToken = r.jsonPath().getString("accessToken");
    }

    @When("the user updates their name to {string} {string}")
    public void updatesName(String firstname, String lastname) {
        String email = lastResponseEmailOrRegistered();
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(String.format(
                        "{\"firstname\":\"%s\",\"lastname\":\"%s\",\"email\":\"%s\"}",
                        firstname, lastname, email))
                .patch(BASE + "/account/me").then().extract().response();
    }

    @When("the user changes their email to {string} using password {string}")
    public void changesEmail(String newEmailSeed, String password) {
        String current = lastResponseEmailOrRegistered();
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(String.format(
                        "{\"firstname\":\"Acc\",\"lastname\":\"Bdd\","
                        + "\"email\":\"%s\",\"currentPassword\":\"%s\"}",
                        uniqueEmail(newEmailSeed), password))
                .patch(BASE + "/account/me").then().extract().response();
        String rotated = lastResponse.jsonPath().getString("tokens.accessToken");
        if (rotated != null) {
            accessToken = rotated;
        }
        // 'current' intentionally unused further — kept for readability of the flow.
        assertThat(current).isNotBlank();
    }

    @When("the user changes their password from {string} to {string}")
    public void changesPassword(String oldPw, String newPw) {
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(String.format(
                        "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}",
                        oldPw, newPw, newPw))
                .post(BASE + "/account/change-password").then().extract().response();
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        accessToken = lastResponse.jsonPath().getString("accessToken");
    }

    @When("the user deletes their account with password {string}")
    public void deletesAccount(String password) {
        lastResponse = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(String.format("{\"password\":\"%s\"}", password))
                .delete(BASE + "/account/me").then().extract().response();
        assertThat(lastResponse.statusCode()).isEqualTo(204);
    }

    // ------------------------------------------------------------------

    @Then("the account response shows firstname {string} and no new tokens")
    public void accountShowsFirstname(String firstname) {
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        assertThat(lastResponse.jsonPath().getString("account.firstname")).isEqualTo(firstname);
        assertThat(lastResponse.jsonPath().getString("tokens")).isNull();
    }

    @Then("the response contains a fresh token pair")
    public void responseContainsFreshPair() {
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        assertThat(lastResponse.jsonPath().getString("tokens.accessToken")).isNotBlank();
        assertThat(lastResponse.jsonPath().getString("tokens.refreshToken")).isNotBlank();
    }

    @Then("the request fails with {int} and error code {string}")
    public void requestFailsWith(int status, String errorCode) {
        assertThat(lastResponse.statusCode()).isEqualTo(status);
        assertThat(lastResponse.jsonPath().getString("errorCode")).isEqualTo(errorCode);
    }

    @And("login with the old email {string} and password {string} fails with 401")
    public void oldLoginFails(String emailSeed, String password) {
        assertThat(login(uniqueEmail(emailSeed), password).statusCode()).isEqualTo(401);
    }

    @And("login with the new email {string} and password {string} succeeds")
    public void newLoginSucceeds(String emailSeed, String password) {
        assertThat(login(uniqueEmail(emailSeed), password).statusCode()).isEqualTo(200);
    }

    @And("registering again with email {string} and password {string} succeeds")
    public void reRegisterSucceeds(String emailSeed, String password) {
        // The seed maps to the SAME generated address the deleted account used —
        // proving the tombstone freed the email.
        assertThat(register(uniqueEmail(emailSeed), password).statusCode()).isEqualTo(201);
    }

    /** The email the current scenario registered with (single-user scenarios). */
    private String lastResponseEmailOrRegistered() {
        return emailCache.values().iterator().next();
    }
}
