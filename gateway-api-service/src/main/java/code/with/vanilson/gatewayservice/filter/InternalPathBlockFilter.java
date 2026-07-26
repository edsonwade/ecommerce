package code.with.vanilson.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * InternalPathBlockFilter — hard edge boundary for service-to-service paths (F7 hardening).
 * <p>
 * <strong>The gap this closes.</strong> Every {@code /internal} endpoint in the platform was documented
 * as "not routed by the gateway", and that was never true: the route table matches whole service
 * prefixes ({@code Path=/api/v1/orders/**}, {@code Path=/api/v1/customers/**}), which also match
 * {@code /api/v1/orders/internal/**} and {@code /api/v1/customers/internal/**}. Being absent from
 * {@code gateway.public-paths} only stops <em>anonymous</em> callers — any holder of a valid JWT was
 * proxied straight through to a service-to-service endpoint. This filter makes the documented
 * behaviour real: internal paths are terminated at the edge, for everyone, authenticated or not.
 * <p>
 * <strong>Why 404 and not 403.</strong> A 403 confirms the endpoint exists. The platform already
 * settled this trade-off for suspended products (Fase 3, decision D4): hidden resources answer with
 * the same 404 as resources that never existed, so an attacker learns nothing from the status code.
 * Internal endpoints follow the same rule — from outside, they simply do not exist.
 * <p>
 * <strong>Ordering.</strong> Runs at {@code HIGHEST_PRECEDENCE + 5}: after {@link RequestIdFilter}
 * (+0), so a rejection still carries its correlation id in the logs, and before
 * {@link JwtAuthenticationFilter} (+10), so a blocked path costs no token parsing, no tenant lookup
 * (+20) and no load-shedding capacity (+30). Nothing downstream ever observes the request.
 * <p>
 * <strong>Legitimate callers are unaffected.</strong> Real service-to-service traffic never crosses the
 * gateway: {@code CustomerRegistrationClient} (auth → customer) and {@code OrderClient}
 * (product → order) are Feign clients bound to the service host directly on {@code services-net}
 * ({@code customer-url}, {@code order-url}). This filter only removes the accidental public route.
 * <p>
 * The patterns are configuration ({@code gateway.blocked-paths}), so a future {@code /internal}
 * surface on any service is covered by the wildcard without touching this class.
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Component
public class InternalPathBlockFilter implements GlobalFilter, Ordered {

    /** Message key for the deliberately indistinguishable "not found" answer. */
    private static final String NOT_FOUND_KEY = "gateway.path.not.found";

    private final MessageSource messageSource;
    private final List<String> blockedPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * @param blockedPaths ant patterns terminated at the edge. The default covers the {@code /internal}
     *                     segment of every service prefix — both the bare path and anything under it,
     *                     because {@code /internal} and {@code /internal/x} are distinct matches.
     */
    public InternalPathBlockFilter(
            MessageSource messageSource,
            @Value("${gateway.blocked-paths:/api/v1/*/internal,/api/v1/*/internal/**}") List<String> blockedPaths) {
        this.messageSource = messageSource;
        this.blockedPaths = blockedPaths;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isBlocked(path)) {
            // WARN, not DEBUG: reaching this line means something outside the cluster asked for a
            // service-to-service endpoint. That is worth seeing in the logs.
            log.warn("[InternalPathBlockFilter] Blocked external access to internal path=[{}] method=[{}]",
                    path, exchange.getRequest().getMethod());
            return writeNotFound(exchange);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // +5: strictly between RequestIdFilter (+0) and JwtAuthenticationFilter (+10). Distinct
        // orders matter — equal orders make GlobalFilter execution sequence non-deterministic.
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    // Ant-style matching, mirroring TenantValidationFilter#isPublicPath — a naive contains("/internal")
    // would also block a legitimate resource that merely embeds the word (e.g. "/api/v1/x/internally").
    private boolean isBlocked(String path) {
        return blockedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> writeNotFound(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().add("Content-Type", "application/json");
        String message = messageSource.getMessage(NOT_FOUND_KEY, null, LocaleContextHolder.getLocale());
        String body = String.format(
                "{\"status\":%d,\"errorCode\":\"%s\",\"message\":\"%s\"}",
                HttpStatus.NOT_FOUND.value(), NOT_FOUND_KEY, message);
        var buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }
}
