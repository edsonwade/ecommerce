package code.with.vanilson.productservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductSecurityConfig — public endpoint allowlist")
class ProductSecurityConfigTest {

    @Test
    @DisplayName("allows the Prometheus scrape endpoint without authentication")
    void publicEndpoints_containPrometheusScrape() {
        assertThat(ProductSecurityConfig.PUBLIC_ENDPOINTS).contains("/actuator/prometheus");
    }

    @Test
    @DisplayName("keeps health probes public (regression guard)")
    void publicEndpoints_keepHealthProbes() {
        assertThat(ProductSecurityConfig.PUBLIC_ENDPOINTS).contains("/actuator/health/**");
    }

    @Test
    @DisplayName("never opens the whole actuator surface")
    void publicEndpoints_doNotExposeWholeActuator() {
        assertThat(ProductSecurityConfig.PUBLIC_ENDPOINTS)
                .doesNotContain("/actuator/**", "/actuator/env", "/actuator/heapdump");
    }
}
