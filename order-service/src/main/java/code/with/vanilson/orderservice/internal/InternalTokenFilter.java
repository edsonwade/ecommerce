package code.with.vanilson.orderservice.internal;

import code.with.vanilson.tenantcontext.internal.InternalTokenAuthenticationFilter;
import code.with.vanilson.tenantcontext.internal.InternalTokenAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * InternalTokenFilter — service-to-service trust boundary for order-service (F7, Layer 2).
 * <p>
 * Guards {@code /api/v1/orders/internal/**} with the shared secret carried in
 * {@code X-Internal-Token}. Since the hardening pass this class is a <strong>thin adapter</strong>:
 * the security decision itself lives in {@link InternalTokenAuthenticator} inside the shared
 * {@code tenant-context} library, so order-service and any future service with an {@code /internal}
 * surface enforce byte-for-byte the same rules instead of each maintaining a copy. Full design:
 * {@code docs/engineering/service-to-service-auth.md}.
 * <p>
 * What the shared core adds over the original inline implementation:
 * <ul>
 *   <li><strong>Rotation.</strong> Several secrets are valid at once, so a new value can be rolled out
 *       and the old one retired afterwards, instead of both services having to restart together.</li>
 *   <li><strong>Caller identity.</strong> Secrets are configured per caller, so a call can be
 *       attributed to product-service rather than to "whoever holds the string", and one caller can be
 *       revoked without cutting off the rest.</li>
 * </ul>
 * <p>
 * Unchanged and still load-bearing: comparison is constant-time; a blank configured secret rejects
 * everything (a misconfiguration can never silently open the endpoint); the secret is never logged;
 * and {@code /internal/**} stays {@code permitAll} in the security chain because <em>this</em> filter
 * is its authenticator, which keeps the JWT filter off a call that carries no user.
 * <p>
 * Defence in depth around it: the gateway now terminates {@code /internal} at the edge
 * ({@code InternalPathBlockFilter}, 404) — previously the path was reachable by any authenticated
 * caller, since the route predicate {@code Path=/api/v1/orders/**} matches it.
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Component
public class InternalTokenFilter extends InternalTokenAuthenticationFilter {

    /**
     * Header carrying the shared S2S secret. Value is never logged.
     * Kept here as the name order-service code and tests refer to; it is the same header the shared
     * filter reads.
     */
    public static final String INTERNAL_TOKEN_HEADER = InternalTokenAuthenticationFilter.TOKEN_HEADER;

    /** Path prefix this filter guards. Anything else is skipped. */
    private static final String INTERNAL_PATH_PREFIX = "/api/v1/orders/internal";

    /** messages.properties key for the 401 body — order-service keeps its own established key. */
    private static final String INVALID_TOKEN_KEY = "order.internal.token.invalid";

    private static final List<String> GUARDED_PATHS =
            List.of(INTERNAL_PATH_PREFIX, INTERNAL_PATH_PREFIX + "/**");

    /**
     * Primary (Spring-injected) constructor.
     * <p>
     * The rich {@link InternalTokenAuthenticator} — per-caller secrets plus the rotation window — is
     * contributed by {@code InternalTokenAutoConfiguration} in tenant-context. It is injected through
     * an {@link ObjectProvider} <strong>on purpose</strong>: this filter is an order-service
     * {@code @Component} (in the application base package), so a {@code @WebMvcTest} slice instantiates
     * it, but a slice does not load tenant-context's auto-configuration and therefore has no
     * authenticator bean. A hard constructor dependency made every slice context fail to load. The
     * provider is always injectable; when the bean is genuinely absent the filter falls back to an
     * authenticator built from the legacy single-secret property, which is fail-closed (blank ⇒ reject
     * everything) and never invoked in a slice anyway ({@code addFilters = false} / non-internal paths).
     * <p>
     * Explicitly {@code @Autowired} because this class offers additional constructors — without the
     * marker the container cannot choose between them.
     */
    @Autowired
    public InternalTokenFilter(ObjectProvider<InternalTokenAuthenticator> authenticatorProvider,
                               MessageSource messageSource,
                               ObjectMapper objectMapper,
                               @Value("${application.security.internal-token:}") String legacyToken) {
        super(authenticatorProvider.getIfAvailable(
                        () -> new InternalTokenAuthenticator(Map.of(), legacyToken, false)),
                messageSource, objectMapper, GUARDED_PATHS, INVALID_TOKEN_KEY);
    }

    /**
     * Direct-authenticator constructor, for tests that build a specific {@link InternalTokenAuthenticator}
     * (per-caller / rotation scenarios). Not selected by Spring — the {@code ObjectProvider} constructor
     * above is the injected one.
     */
    public InternalTokenFilter(InternalTokenAuthenticator authenticator,
                               MessageSource messageSource,
                               ObjectMapper objectMapper) {
        super(authenticator, messageSource, objectMapper, GUARDED_PATHS, INVALID_TOKEN_KEY);
    }

    /**
     * Single-secret convenience constructor, retained for direct construction (tests and any caller
     * that only has one shared value). Behaves exactly like the original implementation: the secret is
     * accepted from any caller, and a blank value rejects everything.
     */
    public InternalTokenFilter(String configuredToken,
                               MessageSource messageSource,
                               ObjectMapper objectMapper) {
        super(new InternalTokenAuthenticator(Map.of(), configuredToken, false),
                messageSource, objectMapper, GUARDED_PATHS, INVALID_TOKEN_KEY);
    }

    /**
     * Redeclared purely for visibility: {@code shouldNotFilter} is {@code protected} on the shared
     * parent, which lives in another package, so order-service's own tests could no longer reach it
     * through an {@code InternalTokenFilter} reference. The behaviour is entirely the parent's.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return super.shouldNotFilter(request);
    }
}
