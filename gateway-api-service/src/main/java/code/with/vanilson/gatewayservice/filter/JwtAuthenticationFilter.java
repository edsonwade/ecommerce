package code.with.vanilson.gatewayservice.filter;

import code.with.vanilson.gatewayservice.exception.JwtAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.List;

/**
 * JwtAuthenticationFilter
 * <p>
 * Global filter that runs at the highest priority (Ordered.HIGHEST_PRECEDENCE + 10).
 * Validates JWT tokens and enriches downstream requests with tenant/user headers.
 * Public endpoints (configured via gateway.public-paths) bypass authentication.
 * <p>
 * On success: adds X-User-ID, X-Tenant-ID, X-User-Role headers for downstream services.
 * On failure: throws JwtAuthenticationException → handled by GatewayGlobalExceptionHandler.
 * All messages resolved from messages.properties.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";

    private final MessageSource messageSource;
    private final SecretKey signingKey;
    private final List<String> publicPaths;

    public JwtAuthenticationFilter(
            MessageSource messageSource,
            @Value("${gateway.jwt.secret}") String jwtSecret,
            @Value("${gateway.public-paths:/api/v1/auth/**,/actuator/**}") List<String> publicPaths) {
        this.messageSource = messageSource;
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.publicPaths = publicPaths;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");

        log.debug("[JwtAuthenticationFilter] Processing request path=[{}] requestId=[{}]", path, requestId);

        // Skip JWT validation for public paths
        if (isPublicPath(path)) {
            log.debug("[JwtAuthenticationFilter] Public path — skipping JWT validation: path=[{}]", path);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            String message = messageSource.getMessage(
                    "gateway.auth.missing.token", null, LocaleContextHolder.getLocale());
            log.warn(messageSource.getMessage(
                    "gateway.log.jwt.rejected",
                    new Object[]{"missing or malformed Authorization header",
                            exchange.getRequest().getRemoteAddress()},
                    LocaleContextHolder.getLocale()));
            throw new JwtAuthenticationException(message, "gateway.auth.missing.token");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
            String role = claims.get(CLAIM_ROLE, String.class);

            log.info(messageSource.getMessage(
                    "gateway.log.jwt.validated",
                    new Object[]{userId, tenantId, role},
                    LocaleContextHolder.getLocale()));

            // Enrich downstream request with resolved identity headers
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-ID", userId != null ? userId : "")
                    .header("X-Tenant-ID", tenantId != null ? tenantId : "anonymous")
                    .header("X-User-Role", role != null ? role : "GUEST")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException ex) {
            String message = messageSource.getMessage(
                    "gateway.auth.expired.token", null, LocaleContextHolder.getLocale());
            log.warn(messageSource.getMessage(
                    "gateway.log.jwt.rejected",
                    new Object[]{"token expired", exchange.getRequest().getRemoteAddress()},
                    LocaleContextHolder.getLocale()));
            throw new JwtAuthenticationException(message, "gateway.auth.expired.token", ex);

        } catch (MalformedJwtException ex) {
            String message = messageSource.getMessage(
                    "gateway.auth.malformed.token", null, LocaleContextHolder.getLocale());
            log.warn(messageSource.getMessage(
                    "gateway.log.jwt.rejected",
                    new Object[]{"malformed token", exchange.getRequest().getRemoteAddress()},
                    LocaleContextHolder.getLocale()));
            throw new JwtAuthenticationException(message, "gateway.auth.malformed.token", ex);

        } catch (SignatureException ex) {
            String message = messageSource.getMessage(
                    "gateway.auth.signature.invalid", null, LocaleContextHolder.getLocale());
            log.warn(messageSource.getMessage(
                    "gateway.log.jwt.rejected",
                    new Object[]{"invalid signature", exchange.getRequest().getRemoteAddress()},
                    LocaleContextHolder.getLocale()));
            throw new JwtAuthenticationException(message, "gateway.auth.signature.invalid", ex);

        } catch (UnsupportedJwtException ex) {
            String message = messageSource.getMessage(
                    "gateway.auth.unsupported.token", null, LocaleContextHolder.getLocale());
            log.warn(messageSource.getMessage(
                    "gateway.log.jwt.rejected",
                    new Object[]{"unsupported token type", exchange.getRequest().getRemoteAddress()},
                    LocaleContextHolder.getLocale()));
            throw new JwtAuthenticationException(message, "gateway.auth.unsupported.token", ex);

        } catch (JwtException ex) {
            String message = messageSource.getMessage(
                    "gateway.auth.invalid.token", null, LocaleContextHolder.getLocale());
            log.warn(messageSource.getMessage(
                    "gateway.log.jwt.rejected",
                    new Object[]{"generic JWT error: " + ex.getMessage(),
                            exchange.getRequest().getRemoteAddress()},
                    LocaleContextHolder.getLocale()));
            throw new JwtAuthenticationException(message, "gateway.auth.invalid.token", ex);
        }
    }

    @Override
    public int getOrder() {
        // Run before rate limiting and routing filters
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(pattern -> {
            String normalizedPattern = pattern.replace("/**", "");
            return path.startsWith(normalizedPattern);
        });
    }
}
