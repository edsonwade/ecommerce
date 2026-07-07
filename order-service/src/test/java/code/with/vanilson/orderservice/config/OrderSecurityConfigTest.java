package code.with.vanilson.orderservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderSecurityConfig — public endpoint allowlist")
class OrderSecurityConfigTest {

    @Test
    @DisplayName("allows the Prometheus scrape endpoint without authentication")
    void publicEndpoints_containPrometheusScrape() {
        assertThat(OrderSecurityConfig.PUBLIC_ENDPOINTS).contains("/actuator/prometheus");
    }

    @Test
    @DisplayName("keeps health probes public (regression guard)")
    void publicEndpoints_keepHealthProbes() {
        assertThat(OrderSecurityConfig.PUBLIC_ENDPOINTS)
                .contains("/actuator/health/**", "/actuator/info");
    }

    @Test
    @DisplayName("never opens the whole actuator surface")
    void publicEndpoints_doNotExposeWholeActuator() {
        assertThat(OrderSecurityConfig.PUBLIC_ENDPOINTS)
                .doesNotContain("/actuator/**", "/actuator/env", "/actuator/heapdump");
    }
}
