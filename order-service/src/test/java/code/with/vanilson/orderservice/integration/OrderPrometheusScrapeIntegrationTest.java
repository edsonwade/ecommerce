package code.with.vanilson.orderservice.integration;

import code.with.vanilson.orderservice.customer.CustomerClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
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
 * PostgreSQL (Testcontainers) + EmbeddedKafka — same recipe as
 * {@link OrderSagaIntegrationTest}. Never H2.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=order-prometheus-scrape-test",
                "management.endpoints.web.exposure.include=health,prometheus"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.authorized", "payment.failed", "inventory.insufficient", "order-topic"},
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
@ActiveProfiles("test")
// Metrics exporters are disabled by default in Spring Boot tests; without this
// the PrometheusMeterRegistry bean is never created and /actuator/prometheus
// does not exist in the test context.
@AutoConfigureObservability(tracing = false)
@DisplayName("Prometheus scrape — order-service (integration, Testcontainers PostgreSQL + EmbeddedKafka)")
class OrderPrometheusScrapeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_prometheus_test")
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

    @MockBean
    CustomerClient customerClient;

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
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/orders/1", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
