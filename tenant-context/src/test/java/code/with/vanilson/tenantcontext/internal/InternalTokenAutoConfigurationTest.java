package code.with.vanilson.tenantcontext.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InternalTokenAutoConfigurationTest — wiring tests for the shared S2S auto-configuration.
 * <p>
 * The interesting behaviour here is not "is a bean created" but the migration rule: the legacy
 * platform-wide secret must stop being accepted the moment per-caller secrets exist, so that adding
 * the stronger credential cannot leave the weaker one silently open behind it.
 *
 * @author vamuhong
 * @version 1.0
 */
@DisplayName("InternalTokenAutoConfiguration — wiring tests")
class InternalTokenAutoConfigurationTest {

    private static final String LEGACY = "legacy-platform-wide-secret";
    private static final String PER_CALLER = "product-service-secret";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InternalTokenAutoConfiguration.class));

    // -------------------------------------------------------
    @Nested
    @DisplayName("Legacy single secret")
    class Legacy {

        @Test
        @DisplayName("is honoured while no per-caller secret is configured (untouched deployments keep working)")
        void legacyAloneStillWorks() {
            runner.withPropertyValues("application.security.internal-token=" + LEGACY)
                    .run(context -> {
                        InternalTokenAuthenticator auth = context.getBean(InternalTokenAuthenticator.class);
                        assertThat(auth.isConfigured()).isTrue();
                        assertThat(auth.authenticate(LEGACY, null))
                                .contains(InternalTokenAuthenticator.UNIDENTIFIED_CALLER);
                    });
        }

        @Test
        @DisplayName("STOPS being accepted as soon as a per-caller secret is configured")
        void legacyIgnoredOncePerCallerExists() {
            runner.withPropertyValues(
                            "application.security.internal-token=" + LEGACY,
                            "application.security.internal.accepted.product-service[0]=" + PER_CALLER)
                    .run(context -> {
                        InternalTokenAuthenticator auth = context.getBean(InternalTokenAuthenticator.class);
                        // The weaker credential must not remain a parallel way in.
                        assertThat(auth.authenticate(LEGACY, "product-service")).isEmpty();
                        assertThat(auth.authenticate(LEGACY, null)).isEmpty();
                        // The configured caller still authenticates.
                        assertThat(auth.authenticate(PER_CALLER, "product-service")).contains("product-service");
                    });
        }

        @Test
        @DisplayName("a blank per-caller entry does NOT count as configured — legacy still carries the service")
        void blankPerCallerEntryDoesNotDisableLegacy() {
            // Guards the rotation placeholder ${INTERNAL_SERVICE_TOKEN_PREVIOUS:} resolving to empty:
            // that must not be mistaken for "per-caller is configured" and cut off a live deployment.
            runner.withPropertyValues(
                            "application.security.internal-token=" + LEGACY,
                            "application.security.internal.accepted.product-service[0]=")
                    .run(context -> {
                        InternalTokenAuthenticator auth = context.getBean(InternalTokenAuthenticator.class);
                        assertThat(auth.authenticate(LEGACY, null))
                                .contains(InternalTokenAuthenticator.UNIDENTIFIED_CALLER);
                    });
        }
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Defaults and properties")
    class Defaults {

        @Test
        @DisplayName("with nothing configured the authenticator exists but rejects everything (fail-closed)")
        void failsClosedByDefault() {
            runner.run(context -> {
                InternalTokenAuthenticator auth = context.getBean(InternalTokenAuthenticator.class);
                assertThat(auth.isConfigured()).isFalse();
                assertThat(auth.authenticate("anything", "product-service")).isEmpty();
            });
        }

        @Test
        @DisplayName("require-caller-header is propagated — an anonymous but valid token is rejected")
        void requireCallerHeaderIsHonoured() {
            runner.withPropertyValues(
                            "application.security.internal.accepted.product-service[0]=" + PER_CALLER,
                            "application.security.internal.require-caller-header=true")
                    .run(context -> {
                        InternalTokenAuthenticator auth = context.getBean(InternalTokenAuthenticator.class);
                        assertThat(auth.authenticate(PER_CALLER, null)).isEmpty();
                        assertThat(auth.authenticate(PER_CALLER, "product-service")).contains("product-service");
                    });
        }

        @Test
        @DisplayName("rotation: both accepted values authenticate the same caller")
        void rotationWindowIsWired() {
            runner.withPropertyValues(
                            "application.security.internal.accepted.product-service[0]=new-secret",
                            "application.security.internal.accepted.product-service[1]=old-secret")
                    .run(context -> {
                        InternalTokenAuthenticator auth = context.getBean(InternalTokenAuthenticator.class);
                        assertThat(auth.authenticate("new-secret", "product-service")).contains("product-service");
                        assertThat(auth.authenticate("old-secret", "product-service")).contains("product-service");
                    });
        }
    }
}
