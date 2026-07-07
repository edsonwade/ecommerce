package code.with.vanilson.productservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * PostgreSQL (Testcontainers) + EmbeddedKafka. Never H2.
 * <p>
 * Redis is mocked (same pattern as cart/customer): the L2 cache is not what
 * this test proves, and {@code ProductServiceConfig.flushStaleProductCache()}
 * would otherwise execute a live Redis command at startup — on this host the
 * Lettuce client hangs indefinitely inside the test JVM even with a healthy
 * Redis container.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=product-prometheus-scrape-test",
                "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==",
                "management.endpoints.web.exposure.include=health,prometheus",
                // Redis is mocked — the reactive Redis template/health contributor
                // would otherwise demand a real reactive connection factory;
                // same fix as customer-service.
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"order.requested", "payment.failed", "inventory.reserved", "inventory.released"},
        brokerProperties = {"auto.create.topics.enable=true"})
@ActiveProfiles("test")
// Metrics exporters are disabled by default in Spring Boot tests; without this
// the PrometheusMeterRegistry bean is never created and /actuator/prometheus
// does not exist in the test context.
@AutoConfigureObservability(tracing = false)
@DisplayName("Prometheus scrape — product-service (integration, Testcontainers PostgreSQL + Redis + EmbeddedKafka)")
class ProductPrometheusScrapeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("product_prometheus_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // No live Redis in this context — flushStaleProductCache() runs a Redis
    // command at startup and cacheManager() needs a connection factory bean.
    @MockBean
    StringRedisTemplate stringRedisTemplate;

    @MockBean
    RedisConnectionFactory redisConnectionFactory;

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
    @DisplayName("seller-only endpoint still returns 401 without credentials")
    void sellerEndpoint_unauthenticated_returns401() {
        // The public catalogue (GET /api/v1/products) is deliberately open,
        // so the auth guard uses the seller-only /mine endpoint.
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/products/mine", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
