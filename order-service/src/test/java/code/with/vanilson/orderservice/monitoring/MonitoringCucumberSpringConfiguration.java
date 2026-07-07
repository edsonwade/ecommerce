package code.with.vanilson.orderservice.monitoring;

import code.with.vanilson.orderservice.customer.CustomerClient;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Spring context for the @monitoring BDD scenarios — real PostgreSQL
 * (Testcontainers) + EmbeddedKafka, mirroring OrderSagaIntegrationTest.
 * The container is started manually because the JUnit @Testcontainers
 * extension does not run under the Cucumber engine. Never H2.
 */
@CucumberContextConfiguration
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=order-monitoring-bdd-test",
                "management.endpoints.web.exposure.include=health,prometheus"
        })
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.authorized", "payment.failed", "inventory.insufficient", "order-topic"},
        brokerProperties = {"auto.create.topics.enable=true"})
@ActiveProfiles("test")
// Metrics exporters are disabled by default in Spring Boot tests; needed so the
// scenarios can hit a real /actuator/prometheus endpoint.
@AutoConfigureObservability(tracing = false)
@SuppressWarnings("resource")
public class MonitoringCucumberSpringConfiguration {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_monitoring_bdd_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start(); // stopped by Testcontainers' Ryuk reaper after the JVM exits
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    CustomerClient customerClient;
}
