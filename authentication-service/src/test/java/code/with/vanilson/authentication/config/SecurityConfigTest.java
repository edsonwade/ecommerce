package code.with.vanilson.authentication.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityConfig — public actuator allowlist")
class SecurityConfigTest {

    @Test
    @DisplayName("allows the Prometheus scrape endpoint without authentication")
    void publicActuatorEndpoints_containPrometheusScrape() {
        assertThat(SecurityConfig.PUBLIC_ACTUATOR_ENDPOINTS).contains("/actuator/prometheus");
    }

    @Test
    @DisplayName("keeps health probes public (regression guard)")
    void publicActuatorEndpoints_keepHealthProbes() {
        assertThat(SecurityConfig.PUBLIC_ACTUATOR_ENDPOINTS)
                .contains("/actuator/health", "/actuator/health/**");
    }

    @Test
    @DisplayName("never opens the whole actuator surface")
    void publicActuatorEndpoints_doNotExposeWholeActuator() {
        assertThat(SecurityConfig.PUBLIC_ACTUATOR_ENDPOINTS)
                .doesNotContain("/actuator/**", "/actuator/env", "/actuator/heapdump", "/actuator/beans");
    }
}
