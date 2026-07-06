package code.with.vanilson.configservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigServerSecurityConfig — public actuator allowlist")
class ConfigServerSecurityConfigTest {

    @Test
    @DisplayName("allows the Prometheus scrape endpoint without authentication")
    void publicActuatorEndpoints_containPrometheusScrape() {
        assertThat(ConfigServerSecurityConfig.PUBLIC_ACTUATOR_ENDPOINTS).contains("/actuator/prometheus");
    }

    @Test
    @DisplayName("keeps health and info probes public (regression guard)")
    void publicActuatorEndpoints_keepHealthAndInfo() {
        assertThat(ConfigServerSecurityConfig.PUBLIC_ACTUATOR_ENDPOINTS)
                .contains("/actuator/health", "/actuator/info");
    }

    @Test
    @DisplayName("never opens config properties or the whole actuator surface")
    void publicActuatorEndpoints_doNotExposeSensitiveSurface() {
        assertThat(ConfigServerSecurityConfig.PUBLIC_ACTUATOR_ENDPOINTS)
                .doesNotContain("/actuator/**", "/actuator/env", "/**");
    }
}
