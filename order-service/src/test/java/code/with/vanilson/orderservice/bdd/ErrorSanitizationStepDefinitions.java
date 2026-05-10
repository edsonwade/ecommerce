package code.with.vanilson.orderservice.bdd;

import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.exception.OrderGlobalExceptionHandler;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Cucumber step definitions for error sanitization BDD scenarios (BUG-005).
 */
public class ErrorSanitizationStepDefinitions {

    private OrderGlobalExceptionHandler handler;
    private MessageSource messageSource;
    private WebRequest webRequest;

    private Exception simulatedException;
    private ResponseEntity<Map<String, Object>> response;

    @Before
    public void setUp() {
        messageSource = Mockito.mock(MessageSource.class);
        webRequest = Mockito.mock(WebRequest.class);
        handler = new OrderGlobalExceptionHandler(messageSource); // assuming constructor injection in reality, but mock works for testing

        when(webRequest.getDescription(false)).thenReturn("uri=/api/v1/test");

        when(messageSource.getMessage(eq("order.error.internal.user"), isNull(), any(Locale.class)))
                .thenReturn("An unexpected error occurred. Please try again later.");
        
        when(messageSource.getMessage(eq("order.error.internal"), any(), any(Locale.class)))
                .thenReturn("order.error.internal");
    }

    @Given("the service encounters an unexpected runtime error")
    public void the_service_encounters_an_unexpected_runtime_error() {
        simulatedException = new RuntimeException("Deep null pointer exception inside service layer");
    }

    @When("the system returns the error response")
    public void the_system_returns_the_error_response() {
        // Simulate the global exception handler kicking in
        response = handler.handleGenericException(simulatedException, webRequest);
    }

    @Then("the response status should be {int}")
    public void the_response_status_should_be(int expectedStatus) {
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @Then("the error message should be {string}")
    public void the_error_message_should_be(String expectedMessage) {
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo(expectedMessage);
    }

    @Then("the error message should not contain {string} or a UUID pattern")
    public void the_error_message_should_not_contain_reference_or_uuid(String referenceText) {
        Map<String, Object> body = response.getBody();
        String msg = (String) body.get("message");
        assertThat(msg).doesNotContain(referenceText);
        assertThat(msg).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Then("the response body should not include internal tracking fields like {string}")
    public void the_response_body_should_not_include_internal_tracking_fields(String fieldName) {
        Map<String, Object> body = response.getBody();
        assertThat(body).doesNotContainKey(fieldName);
        assertThat(body).doesNotContainKey("reference");
    }
}
