package code.with.vanilson.authentication.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * CucumberSpringConfiguration — wires the Spring Boot test context for Cucumber BDD tests.
 *
 * The PostgreSQL container is started in a static initializer (not via @Testcontainers)
 * to guarantee it is running before Spring calls @DynamicPropertySource. Using
 * @Testcontainers + @Container inside a @CucumberContextConfiguration class is unreliable
 * because the Testcontainers JUnit 5 extension lifecycle does not align with Cucumber's
 * context lifecycle.
 *
 * Flyway migrations are applied automatically when the Spring context starts.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CucumberSpringConfiguration {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("auth_bdd")
                .withUsername("bdd_user")
                .withPassword("bdd_pass");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",           POSTGRES::getUsername);
        registry.add("spring.datasource.password",           POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
