package code.with.vanilson.cartservice.integration;

import code.with.vanilson.cartservice.application.CartMapper;
import code.with.vanilson.cartservice.infrastructure.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context proof that Prometheus can scrape this service without
 * credentials: real actuator endpoint, real security filter chain.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
        "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==",
        "management.endpoints.web.exposure.include=health,prometheus"
})
@AutoConfigureMockMvc
// Metrics exporters are disabled by default in Spring Boot tests; without this
// the PrometheusMeterRegistry bean is never created and /actuator/prometheus
// does not exist in the test context.
@AutoConfigureObservability(tracing = false)
@DisplayName("Prometheus scrape — cart-service (integration)")
class CartPrometheusScrapeIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    private CartRepository cartRepository;

    @MockBean
    private CartMapper cartMapper;

    @MockBean
    private MessageSource messageSource;

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
        mockMvc.perform(get("/api/v1/carts/42"))
                .andExpect(status().isUnauthorized());
    }
}
