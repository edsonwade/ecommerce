package code.with.vanilson.tenantcontext.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * InternalTokenProperties — configuration for service-to-service authentication.
 * <p>
 * Binds {@code application.security.internal.*} and, for backwards compatibility, the original
 * single-secret key {@code application.security.internal-token} that Fase 7 shipped with. Both work;
 * a service can adopt the richer form without a flag day.
 *
 * <pre>
 * application:
 *   security:
 *     internal-token: ${INTERNAL_SERVICE_TOKEN}      # legacy single secret (still honoured)
 *     internal:
 *       caller: product-service                       # identity this service sends
 *       token: ${INTERNAL_SERVICE_TOKEN}              # secret this service sends
 *       accepted:                                     # secrets this service ACCEPTS, per caller
 *         product-service:
 *           - ${INTERNAL_SERVICE_TOKEN}               # current
 *           - ${INTERNAL_SERVICE_TOKEN_PREVIOUS:}     # still valid during a rotation
 *       guarded-paths:
 *         - /api/v1/*&#47;internal
 *         - /api/v1/*&#47;internal/**
 * </pre>
 *
 * <p><strong>Why {@code accepted} is a map of lists.</strong> It answers the platform's two weakest
 * points at once. The map key gives the callee a <em>caller identity</em> — previously the token only
 * proved "someone holds the secret", with no way to tell which service, log it, or revoke one caller
 * without breaking the rest. The list gives <em>rotation with an overlap window</em>: publish the new
 * secret as an additional accepted value, roll the callers one at a time, then drop the old value.
 * Without the list, rotating means both services must restart in the same instant or S2S traffic 401s.
 *
 * @author vamuhong
 * @version 1.0
 */
@ConfigurationProperties(prefix = "application.security.internal")
public class InternalTokenProperties {

    /** Identity this service announces in {@code X-Internal-Caller}. Blank ⇒ header not sent. */
    private String caller = "";

    /** Secret this service sends in {@code X-Internal-Token}. Blank ⇒ falls back to the legacy key. */
    private String token = "";

    /**
     * Secrets this service accepts, keyed by caller name. Several values per caller are allowed and
     * are all valid simultaneously — that overlap is what makes a zero-downtime rotation possible.
     */
    private Map<String, List<String>> accepted = new LinkedHashMap<>();

    /**
     * Ant patterns the inbound filter guards; anything else is untouched. The defaults match an
     * {@code /internal} segment anywhere in the URI, so they cover both
     * {@code /api/v1/orders/internal/...} and any future prefix without reconfiguration.
     */
    private List<String> guardedPaths = new ArrayList<>(List.of("/**/internal", "/**/internal/**"));

    /**
     * messages.properties key used for the 401 body. Overridable so a service can keep its own
     * existing key (order-service keeps {@code order.internal.token.invalid}) instead of duplicating one.
     */
    private String messageKey = "internal.token.invalid";

    /**
     * When true the inbound filter requires {@code X-Internal-Caller} to match the {@code accepted}
     * entry the token belongs to. Default false: a caller that presents a valid token but no identity
     * header (an older build, mid-upgrade) is still accepted and simply logged as unidentified.
     * Turn it on once every caller sends the header.
     */
    private boolean requireCallerHeader = false;

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Map<String, List<String>> getAccepted() {
        return accepted;
    }

    public void setAccepted(Map<String, List<String>> accepted) {
        this.accepted = accepted;
    }

    public List<String> getGuardedPaths() {
        return guardedPaths;
    }

    public void setGuardedPaths(List<String> guardedPaths) {
        this.guardedPaths = guardedPaths;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public boolean isRequireCallerHeader() {
        return requireCallerHeader;
    }

    public void setRequireCallerHeader(boolean requireCallerHeader) {
        this.requireCallerHeader = requireCallerHeader;
    }
}
