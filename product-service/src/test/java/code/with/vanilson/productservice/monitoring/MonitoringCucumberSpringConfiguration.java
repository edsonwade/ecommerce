package code.with.vanilson.productservice.monitoring;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Spring context for the @monitoring BDD scenarios — real PostgreSQL
 * (Testcontainers) + EmbeddedKafka. The container is started manually because
 * the JUnit @Testcontainers extension does not run under the Cucumber engine.
 * Never H2.
 * <p>
 * Redis is mocked (same pattern as cart/customer): the L2 cache is not what
 * these scenarios prove, and {@code ProductServiceConfig.flushStaleProductCache()}
 * would otherwise execute a live Redis command at startup — on this host the
 * Lettuce client hangs indefinitely inside the test JVM even with a healthy
 * Redis container.
 */
@CucumberContextConfiguration
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=product-monitoring-bdd-test",
                "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==",
                "management.endpoints.web.exposure.include=health,prometheus",
                // Redis is mocked — the reactive Redis template/health contributor
                // would otherwise demand a real reactive connection factory;
                // same fix as customer-service.
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
        })
@EmbeddedKafka(
        partitions = 1,
        topics = {"order.requested", "payment.failed", "inventory.reserved", "inventory.released"},
        brokerProperties = {"auto.create.topics.enable=true"})
@ActiveProfiles("test")
// Metrics exporters are disabled by default in Spring Boot tests; needed so the
// scenarios can hit a real /actuator/prometheus endpoint.
@AutoConfigureObservability(tracing = false)
@SuppressWarnings("resource")
public class MonitoringCucumberSpringConfiguration {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("product_monitoring_bdd_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start(); // stopped by Testcontainers' Ryuk reaper after the JVM exits
    }

    // No live Redis in this context — flushStaleProductCache() runs a Redis
    // command at startup and cacheManager() needs a connection factory bean.
    @MockBean
    StringRedisTemplate stringRedisTemplate;

    @MockBean
    RedisConnectionFactory redisConnectionFactory;

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
