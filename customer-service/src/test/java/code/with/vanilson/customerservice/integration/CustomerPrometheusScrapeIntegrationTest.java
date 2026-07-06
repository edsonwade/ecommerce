package code.with.vanilson.customerservice.integration;

import code.with.vanilson.customerservice.CustomerMapper;
import code.with.vanilson.customerservice.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context proof that Prometheus can scrape this service without
 * credentials: real actuator endpoint, real security filter chain.
 * <p>
 * No database is used — MongoDB/Redis are mocked and their auto-configuration
 * excluded, matching the project's Cucumber context. Never H2 or embedded DBs.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration",
        "management.health.redis.enabled=false",
        "management.endpoints.web.exposure.include=health,prometheus"
})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
@DisplayName("Prometheus scrape — customer-service (integration)")
class CustomerPrometheusScrapeIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean private MongoTemplate           mongoTemplate;
    @MockBean private CustomerRepository       customerRepository;
    @MockBean private CustomerMapper           customerMapper;
    @MockBean private MessageSource            messageSource;
    @MockBean private RedisConnectionFactory   redisConnectionFactory;
    @MockBean private StringRedisTemplate      stringRedisTemplate;

    @Test
    @DisplayName("GET /actuator/prometheus without credentials returns 200 with metrics")
    void prometheusScrape_unauthenticated_returns200WithMetrics() throws Exception {
        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("# HELP");
    }

    @Test
    @DisplayName("business endpoints still return 401 without credentials")
    void businessEndpoint_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/customers/42"))
                .andExpect(status().isUnauthorized());
    }
}
