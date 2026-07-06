package code.with.vanilson.paymentservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentSecurityConfig — public endpoint allowlist")
class PaymentSecurityConfigTest {

    @Test
    @DisplayName("allows the Prometheus scrape endpoint without authentication")
    void publicEndpoints_containPrometheusScrape() {
        assertThat(PaymentSecurityConfig.PUBLIC_ENDPOINTS).contains("/actuator/prometheus");
    }

    @Test
    @DisplayName("keeps health probes public (regression guard)")
    void publicEndpoints_keepHealthProbes() {
        assertThat(PaymentSecurityConfig.PUBLIC_ENDPOINTS)
                .contains("/actuator/health/**", "/actuator/info");
    }

    @Test
    @DisplayName("never opens the whole actuator surface")
    void publicEndpoints_doNotExposeWholeActuator() {
        assertThat(PaymentSecurityConfig.PUBLIC_ENDPOINTS)
                .doesNotContain("/actuator/**", "/actuator/env", "/actuator/heapdump");
    }
}
