package code.with.vanilson.customerservice.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * AbstractIntegrationTest — Integration Test Base Class
 * <p>
 * Starts MongoDB and Redis containers once per test suite (static) and
 * injects their connection properties via @DynamicPropertySource.
 * <p>
 * All integration tests extend this class.
 * Uses @ActiveProfiles("test") to load application-test.yml from test/resources.
 * <p>
 * Containers are started once and shared across all subclasses for performance.
 * Testcontainers lifecycle: started in static block, stopped by JVM shutdown hook.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final MongoDBContainer mongodb = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0"))
            .withExposedPorts(27017);

    @SuppressWarnings("rawtypes")
    static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--loglevel", "warning");

    static {
        mongodb.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MongoDB
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Disable config server import
        registry.add("spring.cloud.config.enabled",             () -> "false");
        registry.add("spring.cloud.config.import-check.enabled",() -> "false");
        registry.add("eureka.client.enabled",                   () -> "false");
    }
}
