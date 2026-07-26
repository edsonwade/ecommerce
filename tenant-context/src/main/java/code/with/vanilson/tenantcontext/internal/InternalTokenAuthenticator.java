package code.with.vanilson.tenantcontext.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * InternalTokenAuthenticator — the decision logic behind service-to-service authentication.
 * <p>
 * Deliberately a plain object with no Spring, servlet or Feign dependency: the security decision is
 * the part worth testing exhaustively, and keeping it framework-free means it can be unit-tested
 * directly and reused by any transport (the servlet filter today, anything else later).
 *
 * <h2>What it fixes</h2>
 * The original Fase 7 implementation compared one presented value against one configured value. That
 * proved possession of <em>a</em> secret and nothing more:
 * <ul>
 *   <li><strong>No identity</strong> — order-service could not tell product-service from any other
 *       holder of the string, so nothing could be logged, rate-limited or revoked per caller.</li>
 *   <li><strong>No rotation</strong> — one accepted value means the old and new secret can never be
 *       valid at once, so rotating required both services to restart simultaneously.</li>
 * </ul>
 * This class accepts a set of secrets <em>per caller</em>, which answers both.
 *
 * <h2>Fail-closed, always</h2>
 * With nothing configured, {@link #authenticate} rejects everything. A half-configured deployment can
 * never accidentally open an internal endpoint — the failure mode is "nobody gets in", never
 * "everybody gets in".
 *
 * <h2>Timing</h2>
 * Comparison uses {@link MessageDigest#isEqual} and the loop deliberately does <strong>not</strong>
 * stop at the first match: short-circuiting would leak, through response timing, which caller's
 * secret matched and how many candidates were tried.
 *
 * @author vamuhong
 * @version 1.0
 */
public class InternalTokenAuthenticator {

    /** Caller name reported when a request authenticates via the legacy single-secret property. */
    public static final String LEGACY_CALLER = "legacy";

    /** Caller name reported when a valid token arrives without an identity header. */
    public static final String UNIDENTIFIED_CALLER = "unidentified";

    private final Map<String, List<String>> acceptedByCaller;
    private final boolean requireCallerHeader;

    /**
     * @param accepted            secrets accepted per caller; blank values are dropped, so an unset
     *                            placeholder such as {@code ${INTERNAL_SERVICE_TOKEN_PREVIOUS:}}
     *                            simply contributes nothing instead of matching an empty token
     * @param legacyToken         the original single {@code application.security.internal-token};
     *                            when present it is accepted under {@link #LEGACY_CALLER}
     * @param requireCallerHeader whether a matching {@code X-Internal-Caller} is mandatory
     */
    public InternalTokenAuthenticator(Map<String, List<String>> accepted,
                                      String legacyToken,
                                      boolean requireCallerHeader) {
        Map<String, List<String>> sanitised = new LinkedHashMap<>();
        if (accepted != null) {
            accepted.forEach((caller, tokens) -> {
                List<String> usable = new ArrayList<>();
                if (tokens != null) {
                    tokens.stream()
                            .filter(t -> t != null && !t.isBlank())
                            .forEach(usable::add);
                }
                if (caller != null && !caller.isBlank() && !usable.isEmpty()) {
                    sanitised.put(caller, List.copyOf(usable));
                }
            });
        }
        if (legacyToken != null && !legacyToken.isBlank()) {
            sanitised.merge(LEGACY_CALLER, List.of(legacyToken), (existing, added) -> {
                List<String> merged = new ArrayList<>(existing);
                merged.addAll(added);
                return List.copyOf(merged);
            });
        }
        this.acceptedByCaller = Map.copyOf(sanitised);
        this.requireCallerHeader = requireCallerHeader;
    }

    /**
     * Authenticates one internal call.
     *
     * @param presentedToken  the {@code X-Internal-Token} header value
     * @param presentedCaller the {@code X-Internal-Caller} header value; may be null
     * @return the resolved caller name when the call is authentic, otherwise empty. A valid token
     *         with no identity header resolves to {@link #UNIDENTIFIED_CALLER} unless
     *         {@code requireCallerHeader} is on, in which case it is rejected.
     */
    public Optional<String> authenticate(String presentedToken, String presentedCaller) {
        if (presentedToken == null || presentedToken.isBlank() || acceptedByCaller.isEmpty()) {
            return Optional.empty();
        }

        String matched = null;
        for (Map.Entry<String, List<String>> entry : acceptedByCaller.entrySet()) {
            for (String candidate : entry.getValue()) {
                // No early exit — see the class Javadoc on timing.
                if (constantTimeEquals(candidate, presentedToken) && matched == null) {
                    matched = entry.getKey();
                }
            }
        }

        if (matched == null) {
            return Optional.empty();
        }

        boolean claimsIdentity = presentedCaller != null && !presentedCaller.isBlank();
        if (!claimsIdentity) {
            return requireCallerHeader ? Optional.empty() : Optional.of(UNIDENTIFIED_CALLER);
        }

        // A claimed identity must be the one the secret actually belongs to. Accepting a mismatch
        // would let a legitimate holder of one caller's secret impersonate another in the audit log.
        if (LEGACY_CALLER.equals(matched) || matched.equals(presentedCaller)) {
            return Optional.of(LEGACY_CALLER.equals(matched) ? presentedCaller : matched);
        }
        return Optional.empty();
    }

    /** True when at least one secret is configured — i.e. internal calls can succeed at all. */
    public boolean isConfigured() {
        return !acceptedByCaller.isEmpty();
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
