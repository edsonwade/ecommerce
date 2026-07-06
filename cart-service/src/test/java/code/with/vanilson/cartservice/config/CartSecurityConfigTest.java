package code.with.vanilson.cartservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CartSecurityConfig — public endpoint allowlist")
class CartSecurityConfigTest {

    @Test
    @DisplayName("allows the Prometheus scrape endpoint without authentication")
    void publicEndpoints_containPrometheusScrape() {
        assertThat(CartSecurityConfig.PUBLIC_ENDPOINTS).contains("/actuator/prometheus");
    }

    @Test
    @DisplayName("keeps health probes public (regression guard)")
    void publicEndpoints_keepHealthProbes() {
        assertThat(CartSecurityConfig.PUBLIC_ENDPOINTS)
                .contains("/actuator/health/**", "/actuator/info");
    }

    @Test
    @DisplayName("never opens the whole actuator surface")
    void publicEndpoints_doNotExposeWholeActuator() {
        assertThat(CartSecurityConfig.PUBLIC_ENDPOINTS)
                .doesNotContain("/actuator/**", "/actuator/env", "/actuator/heapdump");
    }
}
