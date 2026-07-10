package code.with.vanilson.paymentservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack proof that Prometheus can scrape this service without credentials:
 * real HTTP, real actuator endpoint, real security filter chain, real
 * PostgreSQL (Testcontainers) + EmbeddedKafka. Never H2.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=payment-prometheus-scrape-test",
                // tenant-context's JwtTokenValidator refuses to start without a real secret
                "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==",
                "management.endpoints.web.exposure.include=health,prometheus"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"inventory.reserved", "payment.authorized", "payment.failed", "payment-topic"},
        brokerProperties = {
                "auto.create.topics.enable=true",
                // Cap internal-topic index preallocation — without these the embedded
                // broker preallocates ~2.3 GB of index files per run in %TEMP%.
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
// Metrics exporters are disabled by default in Spring Boot tests; without this
// the PrometheusMeterRegistry bean is never created and /actuator/prometheus
// does not exist in the test context.
@AutoConfigureObservability(tracing = false)
@DisplayName("Prometheus scrape — payment-service (integration, Testcontainers PostgreSQL + EmbeddedKafka)")
class PaymentPrometheusScrapeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_prometheus_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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
    @DisplayName("business endpoints still return 401 without credentials")
    void businessEndpoint_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/payments/1", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
