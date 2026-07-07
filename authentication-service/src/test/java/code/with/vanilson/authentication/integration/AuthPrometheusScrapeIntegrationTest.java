package code.with.vanilson.authentication.integration;

import code.with.vanilson.authentication.infrastructure.CustomerRegistrationClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack proof that Prometheus can scrape this service without credentials:
 * real HTTP, real actuator endpoint, real security filter chain, real
 * PostgreSQL (Testcontainers, Flyway-migrated). Never H2.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // application-test.properties exposes only health — the scrape
                // endpoint must be exposed here on top of being permitted.
                "management.endpoints.web.exposure.include=health,prometheus"
        })
@Testcontainers
@ActiveProfiles("test")
// Metrics exporters are disabled by default in Spring Boot tests; without this
// the PrometheusMeterRegistry bean is never created and /actuator/prometheus
// does not exist in the test context.
@AutoConfigureObservability(tracing = false)
@DisplayName("Prometheus scrape — authentication-service (integration, Testcontainers PostgreSQL)")
class AuthPrometheusScrapeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("auth_prometheus_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // No customer-service in this test — same as the BDD context.
    @MockBean
    CustomerRegistrationClient customerRegistrationClient;

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
    @DisplayName("account endpoint still returns 401 without credentials")
    void accountEndpoint_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/auth/account", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("protected actuator endpoints still return 401 without credentials")
    void protectedActuatorEndpoint_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/env", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
