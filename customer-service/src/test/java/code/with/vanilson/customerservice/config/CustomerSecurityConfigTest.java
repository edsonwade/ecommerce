package code.with.vanilson.customerservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomerSecurityConfig — public endpoint allowlist")
class CustomerSecurityConfigTest {

    @Test
    @DisplayName("allows the Prometheus scrape endpoint without authentication")
    void publicEndpoints_containPrometheusScrape() {
        assertThat(CustomerSecurityConfig.PUBLIC_ENDPOINTS).contains("/actuator/prometheus");
    }

    @Test
    @DisplayName("keeps health probes public (regression guard)")
    void publicEndpoints_keepHealthProbes() {
        assertThat(CustomerSecurityConfig.PUBLIC_ENDPOINTS)
                .contains("/actuator/health/**", "/actuator/info");
    }

    @Test
    @DisplayName("never opens the whole actuator surface")
    void publicEndpoints_doNotExposeWholeActuator() {
        assertThat(CustomerSecurityConfig.PUBLIC_ENDPOINTS)
                .doesNotContain("/actuator/**", "/actuator/env", "/actuator/heapdump");
    }
}
