package code.with.vanilson.gatewayservice.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * TenantKeyResolver
 * <p>
 * Resolves the rate limiting bucket key for Redis token-bucket rate limiting.
 * Priority resolution order:
 *   1. X-Tenant-ID header (set by JwtAuthenticationFilter after JWT validation)
 *   2. X-Forwarded-For IP address (for anonymous/unauthenticated requests)
 *   3. Remote address (last resort)
 * <p>
 * This means authenticated tenants get their own bucket (controlled by tier),
 * while anonymous users share an IP-based bucket.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Component("tenantKeyResolver")
public class TenantKeyResolver implements KeyResolver {

    private final MessageSource messageSource;

    public TenantKeyResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        // Priority 1: Tenant from validated JWT (set by JwtAuthenticationFilter)
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");
        if (tenantId != null && !tenantId.isBlank() && !"anonymous".equals(tenantId)) {
            String bucketKey = "rl:tenant:" + tenantId;
            log.debug(messageSource.getMessage(
                    "gateway.log.ratelimit.applied",
                    new Object[]{tenantId, bucketKey, "?"},
                    LocaleContextHolder.getLocale()));
            return Mono.just(bucketKey);
        }

        // Priority 2: X-Forwarded-For (behind load balancer / CDN)
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For can be comma-separated — take the first (original client IP)
            String clientIp = forwardedFor.split(",")[0].trim();
            return Mono.just("rl:ip:" + clientIp);
        }

        // Priority 3: Direct remote address
        if (exchange.getRequest().getRemoteAddress() != null) {
            String remoteIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just("rl:ip:" + remoteIp);
        }

        return Mono.just("rl:anonymous");
    }
}
