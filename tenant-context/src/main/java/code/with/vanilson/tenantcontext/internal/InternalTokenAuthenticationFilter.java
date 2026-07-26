package code.with.vanilson.tenantcontext.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * InternalTokenAuthenticationFilter — reusable inbound guard for {@code /internal} endpoints.
 * <p>
 * This is the shared half of the platform's service-to-service pattern, extracted into
 * {@code tenant-context} (which every service already embeds, and which already hosts
 * {@code JwtAuthenticationFilter}) so a second, third or fourth {@code /internal} surface does not
 * copy-paste a security control. Wire it by setting {@code application.security.internal.*} — see
 * {@link InternalTokenProperties} — and read
 * {@code docs/engineering/service-to-service-auth.md} before adding a new internal endpoint.
 * <p>
 * On success the resolved caller is published as the request attribute
 * {@link #CALLER_ATTRIBUTE}, so downstream code can log or audit <em>which</em> service called
 * without re-reading headers. On failure the response is a 401 whose body is built from the
 * configured message key — the presented and configured secrets are never logged, echoed or hinted at.
 *
 * @author vamuhong
 * @version 1.0
 * @see InternalTokenAuthenticator
 */
@Slf4j
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

    /** Header carrying the shared secret. Its value is never logged. */
    public static final String TOKEN_HEADER = "X-Internal-Token";

    /** Header carrying the calling service's identity. */
    public static final String CALLER_HEADER = "X-Internal-Caller";

    /** Request attribute holding the authenticated caller name for downstream logging/auditing. */
    public static final String CALLER_ATTRIBUTE = "internal.caller";

    private final InternalTokenAuthenticator authenticator;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    private final List<String> guardedPaths;
    private final String messageKey;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public InternalTokenAuthenticationFilter(InternalTokenAuthenticator authenticator,
                                             MessageSource messageSource,
                                             ObjectMapper objectMapper,
                                             List<String> guardedPaths,
                                             String messageKey) {
        this.authenticator = authenticator;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
        this.guardedPaths = guardedPaths;
        this.messageKey = messageKey;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return guardedPaths.stream().noneMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Optional<String> caller = authenticator.authenticate(
                request.getHeader(TOKEN_HEADER),
                request.getHeader(CALLER_HEADER));

        if (caller.isEmpty()) {
            log.warn("[InternalTokenAuth] Rejected internal call path=[{}] claimedCaller=[{}] — "
                            + "missing, unknown or mismatched credentials",
                    request.getRequestURI(), request.getHeader(CALLER_HEADER));
            writeUnauthorized(response, request.getRequestURI());
            return;
        }

        request.setAttribute(CALLER_ATTRIBUTE, caller.get());
        log.debug("[InternalTokenAuth] Authenticated internal call caller=[{}] path=[{}]",
                caller.get(), request.getRequestURI());
        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        String message = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("errorCode", messageKey);
        body.put("message", message);
        body.put("path", path);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
