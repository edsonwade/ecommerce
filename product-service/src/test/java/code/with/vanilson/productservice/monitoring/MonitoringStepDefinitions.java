package code.with.vanilson.productservice.monitoring;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class MonitoringStepDefinitions {

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<String> metricsResponse;
    private ResponseEntity<String> protectedResponse;

    @When("the monitoring system requests the metrics endpoint without credentials")
    public void theMonitoringSystemRequestsTheMetricsEndpoint() {
        metricsResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
    }

    @Then("the metrics endpoint responds with HTTP 200")
    public void theMetricsEndpointRespondsWith200() {
        assertThat(metricsResponse.getStatusCode().value()).isEqualTo(200);
    }

    @Then("the metrics response contains Prometheus metrics")
    public void theMetricsResponseContainsPrometheusMetrics() {
        assertThat(metricsResponse.getBody()).contains("# HELP");
    }

    @When("an anonymous client requests the seller-only products endpoint")
    public void anAnonymousClientRequestsTheSellerOnlyProductsEndpoint() {
        protectedResponse = restTemplate.getForEntity("/api/v1/products/mine", String.class);
    }

    @Then("the protected endpoint responds with HTTP 401")
    public void theProtectedEndpointRespondsWith401() {
        assertThat(protectedResponse.getStatusCode().value()).isEqualTo(401);
    }
}
