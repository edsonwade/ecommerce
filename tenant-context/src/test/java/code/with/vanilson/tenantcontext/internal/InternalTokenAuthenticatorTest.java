package code.with.vanilson.tenantcontext.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InternalTokenAuthenticatorTest — exhaustive tests of the S2S security decision.
 * <p>
 * This is a security control, so the tests are written around what must NEVER happen: no
 * configuration must never mean "allow", a wrong secret must never resolve a caller, and one caller's
 * secret must never authenticate as another caller.
 *
 * @author vamuhong
 * @version 1.0
 */
@DisplayName("InternalTokenAuthenticator — Unit Tests")
class InternalTokenAuthenticatorTest {

    private static final String PRODUCT = "product-service";
    private static final String ORDER = "order-service";
    private static final String CURRENT = "current-secret-value";
    private static final String PREVIOUS = "previous-secret-value";

    private static Map<String, List<String>> accepted(String caller, String... tokens) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put(caller, List.of(tokens));
        return map;
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Fail-closed by default")
    class FailClosed {

        @Test
        @DisplayName("nothing configured → every call is rejected, including a plausible token")
        void shouldRejectWhenNothingConfigured() {
            InternalTokenAuthenticator auth = new InternalTokenAuthenticator(Map.of(), "", false);

            assertThat(auth.isConfigured()).isFalse();
            assertThat(auth.authenticate(CURRENT, PRODUCT)).isEmpty();
            assertThat(auth.authenticate(null, null)).isEmpty();
        }

        @Test
        @DisplayName("blank configured values are dropped, so an unset placeholder never becomes a valid secret")
        void shouldDropBlankConfiguredTokens() {
            Map<String, List<String>> map = new LinkedHashMap<>();
            map.put(PRODUCT, List.of("", "   "));
            InternalTokenAuthenticator auth = new InternalTokenAuthenticator(map, "", false);

            assertThat(auth.isConfigured()).isFalse();
            // The dangerous case: an empty presented token must not match an empty configured one.
            assertThat(auth.authenticate("", PRODUCT)).isEmpty();
            assertThat(auth.authenticate("   ", PRODUCT)).isEmpty();
        }

        @Test
        @DisplayName("missing or blank presented token → rejected")
        void shouldRejectBlankPresentedToken() {
            InternalTokenAuthenticator auth =
                    new InternalTokenAuthenticator(accepted(PRODUCT, CURRENT), "", false);

            assertThat(auth.authenticate(null, PRODUCT)).isEmpty();
            assertThat(auth.authenticate("", PRODUCT)).isEmpty();
            assertThat(auth.authenticate("  ", PRODUCT)).isEmpty();
        }

        @Test
        @DisplayName("a wrong secret is rejected even from a correctly named caller")
        void shouldRejectWrongToken() {
            InternalTokenAuthenticator auth =
                    new InternalTokenAuthenticator(accepted(PRODUCT, CURRENT), "", false);

            assertThat(auth.authenticate("not-the-secret", PRODUCT)).isEmpty();
        }
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Caller identity")
    class CallerIdentity {

        @Test
        @DisplayName("a valid secret resolves the caller it belongs to")
        void shouldResolveCaller() {
            InternalTokenAuthenticator auth =
                    new InternalTokenAuthenticator(accepted(PRODUCT, CURRENT), "", false);

            assertThat(auth.authenticate(CURRENT, PRODUCT)).contains(PRODUCT);
        }

        @Test
        @DisplayName("one caller's secret must NOT authenticate as another caller")
        void shouldRejectImpersonation() {
            Map<String, List<String>> map = new LinkedHashMap<>();
            map.put(PRODUCT, List.of(CURRENT));
            map.put(ORDER, List.of("order-secret"));
            InternalTokenAuthenticator auth = new InternalTokenAuthenticator(map, "", false);

            // Holds product-service's secret but claims to be order-service.
            assertThat(auth.authenticate(CURRENT, ORDER)).isEmpty();
            // Each is fine as itself.
            assertThat(auth.authenticate(CURRENT, PRODUCT)).contains(PRODUCT);
            assertThat(auth.authenticate("order-secret", ORDER)).contains(ORDER);
        }

        @Test
        @DisplayName("valid secret without an identity header → accepted as unidentified (mid-upgrade tolerance)")
        void shouldAcceptWithoutCallerHeaderByDefault() {
            InternalTokenAuthenticator auth =
                    new InternalTokenAuthenticator(accepted(PRODUCT, CURRENT), "", false);

            assertThat(auth.authenticate(CURRENT, null))
                    .contains(InternalTokenAuthenticator.UNIDENTIFIED_CALLER);
            assertThat(auth.authenticate(CURRENT, "  "))
                    .contains(InternalTokenAuthenticator.UNIDENTIFIED_CALLER);
        }

        @Test
        @DisplayName("with require-caller-header on, an anonymous but valid token is rejected")
        void shouldRequireCallerHeaderWhenEnabled() {
            InternalTokenAuthenticator strict =
                    new InternalTokenAuthenticator(accepted(PRODUCT, CURRENT), "", true);

            assertThat(strict.authenticate(CURRENT, null)).isEmpty();
            assertThat(strict.authenticate(CURRENT, PRODUCT)).contains(PRODUCT);
        }
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Rotation with an overlap window")
    class Rotation {

        @Test
        @DisplayName("both the current and the previous secret authenticate while the overlap lasts")
        void shouldAcceptBothDuringRotation() {
            InternalTokenAuthenticator auth =
                    new InternalTokenAuthenticator(accepted(PRODUCT, CURRENT, PREVIOUS), "", false);

            assertThat(auth.authenticate(CURRENT, PRODUCT)).contains(PRODUCT);
            assertThat(auth.authenticate(PREVIOUS, PRODUCT)).contains(PRODUCT);
        }

        @Test
        @DisplayName("dropping the old value ends the window — the retired secret stops working")
        void shouldRejectRetiredSecretAfterWindowCloses() {
            InternalTokenAuthenticator afterRotation =
                    new InternalTokenAuthenticator(accepted(PRODUCT, CURRENT), "", false);

            assertThat(afterRotation.authenticate(PREVIOUS, PRODUCT)).isEmpty();
            assertThat(afterRotation.authenticate(CURRENT, PRODUCT)).contains(PRODUCT);
        }
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Backwards compatibility with the original single secret")
    class LegacySingleSecret {

        @Test
        @DisplayName("the legacy application.security.internal-token still authenticates")
        void shouldAcceptLegacyToken() {
            InternalTokenAuthenticator auth = new InternalTokenAuthenticator(Map.of(), CURRENT, false);

            assertThat(auth.isConfigured()).isTrue();
            assertThat(auth.authenticate(CURRENT, null))
                    .contains(InternalTokenAuthenticator.UNIDENTIFIED_CALLER);
        }

        @Test
        @DisplayName("a legacy-token caller keeps whatever identity it announces — no impersonation check to apply")
        void shouldReportAnnouncedCallerForLegacyToken() {
            InternalTokenAuthenticator auth = new InternalTokenAuthenticator(Map.of(), CURRENT, false);

            // The legacy secret is shared platform-wide, so it cannot prove which service holds it;
            // the announced name is recorded as-is, and that limitation is why per-caller exists.
            assertThat(auth.authenticate(CURRENT, PRODUCT)).contains(PRODUCT);
        }

        @Test
        @DisplayName("legacy and per-caller secrets coexist during migration")
        void shouldAcceptBothFormsSimultaneously() {
            InternalTokenAuthenticator auth =
                    new InternalTokenAuthenticator(accepted(PRODUCT, CURRENT), "legacy-secret", false);

            assertThat(auth.authenticate(CURRENT, PRODUCT)).contains(PRODUCT);
            Optional<String> viaLegacy = auth.authenticate("legacy-secret", null);
            assertThat(viaLegacy).contains(InternalTokenAuthenticator.UNIDENTIFIED_CALLER);
        }
    }
}
