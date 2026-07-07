package code.with.vanilson.customerservice.bdd;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class MonitoringStepDefinitions {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult metricsResult;
    private MvcResult protectedResult;

    @When("the monitoring system requests the metrics endpoint without credentials")
    public void theMonitoringSystemRequestsTheMetricsEndpoint() throws Exception {
        metricsResult = mockMvc.perform(get("/actuator/prometheus")).andReturn();
    }

    @Then("the metrics endpoint responds with HTTP 200")
    public void theMetricsEndpointRespondsWith200() {
        assertThat(metricsResult.getResponse().getStatus()).isEqualTo(200);
    }

    @Then("the metrics response contains Prometheus metrics")
    public void theMetricsResponseContainsPrometheusMetrics() throws Exception {
        assertThat(metricsResult.getResponse().getContentAsString()).contains("# HELP");
    }

    @When("an anonymous client requests a protected customer endpoint")
    public void anAnonymousClientRequestsAProtectedCustomerEndpoint() throws Exception {
        protectedResult = mockMvc.perform(get("/api/v1/customers/42")).andReturn();
    }

    @Then("the protected endpoint responds with HTTP 401")
    public void theProtectedEndpointRespondsWith401() {
        assertThat(protectedResult.getResponse().getStatus()).isEqualTo(401);
    }
}
