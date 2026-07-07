package code.with.vanilson.configservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context proof that Prometheus can scrape the config server without
 * credentials. The config server is file-based (native profile) — no database,
 * no Docker — so a real HTTP round-trip against the booted server is the
 * end-to-end verification (a config server has no domain controllers to slice).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=native",
                "spring.cloud.config.server.native.search-locations=classpath:/configurations",
                "management.endpoints.web.exposure.include=health,info,prometheus"
        })
@ActiveProfiles("native")
// Metrics exporters are disabled by default in Spring Boot tests; without this
// the PrometheusMeterRegistry bean is never created and /actuator/prometheus 404s.
@AutoConfigureObservability(tracing = false)
@DisplayName("Prometheus scrape — config-service (integration)")
class ConfigServerPrometheusScrapeIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @DisplayName("GET /actuator/prometheus without credentials returns 200 with metrics")
    void prometheusScrape_unauthenticated_returns200WithMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("# HELP");
    }

    @Test
    @DisplayName("config properties still require authentication (401 without credentials)")
    void configEndpoint_unauthenticated_returns401() {
        // A config server serves properties at /{application}/{profile}; this must stay behind basic auth.
        ResponseEntity<String> response = restTemplate.getForEntity("/order-service/native", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
