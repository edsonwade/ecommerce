package code.with.vanilson.orderservice.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * InternalTokenFilter — service-to-service trust boundary (F7, Layer 2).
 * <p>
 * Guards the {@code /api/v1/orders/internal/**} endpoints with a shared secret carried in the
 * {@code X-Internal-Token} header. This is the first official S2S authentication pattern in the
 * platform (before F7 the only protection on {@code /internal} was network trust on
 * {@code services-net} + the endpoint not being whitelisted in the gateway). The secret comes from
 * {@code application.security.internal-token} (Vault {@code internal.service.token} in prod, a
 * docker-compose env default in dev — same mechanism as {@code JWT_SECRET}).
 * <p>
 * Defence-in-depth around this filter:
 * <ol>
 *   <li>Layer 1 — the {@code /internal} path is NOT in the gateway {@code public-paths}, so an
 *       anonymous external caller is rejected at the gateway; the real caller (product-service)
 *       reaches order-service directly on {@code services-net}, bypassing the gateway.</li>
 *   <li>Layer 2 — <b>this filter</b>: {@code X-Internal-Token} missing/blank/mismatched → 401.</li>
 *   <li>Layer 3 — Spring Security leaves {@code /internal/**} {@code permitAll}; this filter is the
 *       authenticator for that path, keeping the JWT filter out of the S2S call.</li>
 * </ol>
 * <p>
 * Fail-closed: if the configured token is blank (unset), every internal call is rejected — a
 * misconfiguration can never silently open the endpoint. The token value is never logged or
 * returned. Comparison is constant-time ({@link MessageDigest#isEqual}). Extends
 * {@link OncePerRequestFilter} so the double registration (bean auto-registration + the explicit
 * {@code addFilterBefore} in {@code OrderSecurityConfig}) still runs the check exactly once.
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    /** Header carrying the shared S2S secret. Value is never logged. */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** Path prefix this filter guards. Anything else is skipped (see {@link #shouldNotFilter}). */
    private static final String INTERNAL_PATH_PREFIX = "/api/v1/orders/internal";

    private static final String INVALID_TOKEN_KEY = "order.internal.token.invalid";

    private final String configuredToken;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(
            @Value("${application.security.internal-token:}") String configuredToken,
            MessageSource messageSource,
            ObjectMapper objectMapper) {
        this.configuredToken = configuredToken;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Only /internal/** is guarded; all other paths are untouched by this S2S filter.
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!isValid(presented)) {
            log.warn("[InternalTokenFilter] Rejected internal call — missing/invalid {} header on path=[{}]",
                    INTERNAL_TOKEN_HEADER, request.getRequestURI());
            writeUnauthorized(response, request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Fail-closed constant-time check: a blank configured token or a blank/absent presented token
     * is always invalid, so a misconfiguration never opens the endpoint.
     */
    private boolean isValid(String presented) {
        if (configuredToken == null || configuredToken.isBlank()
                || presented == null || presented.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        String message = messageSource.getMessage(INVALID_TOKEN_KEY, null, LocaleContextHolder.getLocale());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("errorCode", INVALID_TOKEN_KEY);
        body.put("message", message);
        body.put("path", path);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
