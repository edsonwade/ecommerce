package code.with.vanilson.discoveryservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context proof that Prometheus can scrape the Eureka discovery server.
 * Before this fix the endpoint 404'd because micrometer-registry-prometheus
 * was missing, so no PrometheusMeterRegistry / endpoint existed.
 * <p>
 * No database, no Docker — the Eureka server boots standalone. Exposure is set
 * here via properties because in production it is supplied by the config-server
 * override (discovery-service.yml), which is not available in an isolated test.
 * An Eureka server has no domain controllers to slice, so this HTTP round-trip
 * is the end-to-end verification.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=optional:configserver:",
                "spring.cloud.config.enabled=false",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false",
                "management.endpoints.web.exposure.include=health,prometheus"
        })
// Metrics exporters are disabled by default in Spring Boot tests; without this
// the PrometheusMeterRegistry bean is never created and /actuator/prometheus 404s.
@AutoConfigureObservability(tracing = false)
@DisplayName("Prometheus scrape — discovery-service (integration)")
class DiscoveryPrometheusScrapeIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @DisplayName("GET /actuator/prometheus returns 200 with metrics (no longer 404)")
    void prometheusScrape_returns200WithMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("# HELP");
    }
}
