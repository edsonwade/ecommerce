package code.with.vanilson.gatewayservice.filter;

import code.with.vanilson.gatewayservice.client.TenantServiceClient;
import code.with.vanilson.gatewayservice.client.TenantValidationResponse;
import code.with.vanilson.gatewayservice.exception.JwtAuthenticationException;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * TenantValidationFilter
 * <p>
 * Phase 4 — SaaS Multi-Tenancy enforcement at the gateway edge.
 * <p>
 * Runs at HIGHEST_PRECEDENCE + 20, AFTER JwtAuthenticationFilter (+10).
 * At this point, X-Tenant-ID has already been extracted from the JWT and
 * injected into the request headers by JwtAuthenticationFilter.
 * <p>
 * Responsibilities:
 * 1. Reads X-Tenant-ID header (set by JwtAuthenticationFilter).
 * 2. Skips public paths and anonymous tenants.
 * 3. Calls tenant-service GET /api/v1/tenants/{tenantId}/validate via WebClient.
 * 4. ACTIVE tenant → proceeds, enriches request with X-Tenant-Rate-Limit.
 * 5. SUSPENDED/CANCELLED tenant → 403 Forbidden.
 * 6. Tenant not found → 404 Not Found.
 * 7. tenant-service unavailable → 503 Service Unavailable (fail-open configurable).
 * <p>
 * All messages from messages.properties — no hardcoded strings.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@Component
public class TenantValidationFilter implements GlobalFilter, Ordered {

    private static final String HEADER_TENANT_ID        = "X-Tenant-ID";
    private static final String HEADER_TENANT_RATE_LIMIT = "X-Tenant-Rate-Limit";
    private static final String ANONYMOUS               = "anonymous";

    private final TenantServiceClient tenantServiceClient;
    private final MessageSource       messageSource;
    private final List<String>        publicPaths;

    public TenantValidationFilter(
            TenantServiceClient tenantServiceClient,
            MessageSource messageSource,
            @Value("${gateway.public-paths:/api/v1/auth/**,/actuator/**}") List<String> publicPaths) {
        this.tenantServiceClient = tenantServiceClient;
        this.messageSource       = messageSource;
        this.publicPaths         = publicPaths;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path     = exchange.getRequest().getPath().value();
        String tenantId = exchange.getRequest().getHeaders().getFirst(HEADER_TENANT_ID);

        // Skip: public paths (no auth required)
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Skip: anonymous requests (no tenant claim in JWT — handled by JWT filter)
        if (tenantId == null || tenantId.isBlank() || ANONYMOUS.equals(tenantId)) {
            return chain.filter(exchange);
        }

        log.debug("[TenantValidationFilter] Validating tenant=[{}] path=[{}]", tenantId, path);

        return tenantServiceClient.validate(tenantId)
                .flatMap(response -> handleValidResponse(response, exchange, chain))
                .switchIfEmpty(handleTenantNotFound(exchange, tenantId))
                .onErrorResume(WebClientResponseException.class,
                        ex -> handleClientError(ex, exchange, tenantId))
                .onErrorResume(ex -> handleServiceUnavailable(ex, exchange, tenantId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    // -------------------------------------------------------
    // Private handlers
    // -------------------------------------------------------

    private Mono<Void> handleValidResponse(TenantValidationResponse response,
                                            ServerWebExchange exchange,
                                            GatewayFilterChain chain) {
        if (!"ACTIVE".equalsIgnoreCase(response.status())) {
            String msgKey = "SUSPENDED".equalsIgnoreCase(response.status())
                    ? "gateway.tenant.suspended"
                    : "gateway.tenant.cancelled";
            log.warn("[TenantValidationFilter] Tenant rejected: tenantId=[{}] status=[{}]",
                    response.tenantId(), response.status());
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, msgKey);
        }

        log.debug("[TenantValidationFilter] Tenant ACTIVE: tenantId=[{}] rateLimit=[{}]",
                response.tenantId(), response.rateLimit());

        // Enrich request: forward per-tenant rate limit for downstream services
        ServerWebExchange enriched = exchange.mutate()
                .request(r -> r.header(HEADER_TENANT_RATE_LIMIT,
                        String.valueOf(response.rateLimit())))
                .build();

        return chain.filter(enriched);
    }

    private Mono<Void> handleTenantNotFound(ServerWebExchange exchange, String tenantId) {
        log.warn("[TenantValidationFilter] Tenant not found: tenantId=[{}]", tenantId);
        return writeErrorResponse(exchange, HttpStatus.NOT_FOUND, "gateway.tenant.not.found");
    }

    private Mono<Void> handleClientError(WebClientResponseException ex,
                                          ServerWebExchange exchange,
                                          String tenantId) {
        if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
            log.warn("[TenantValidationFilter] Tenant forbidden by service: tenantId=[{}]", tenantId);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "gateway.tenant.suspended");
        }
        log.error("[TenantValidationFilter] Client error validating tenant=[{}]: status=[{}]",
                tenantId, ex.getStatusCode());
        return writeErrorResponse(exchange, HttpStatus.BAD_GATEWAY, "gateway.error.internal");
    }

    private Mono<Void> handleServiceUnavailable(Throwable ex,
                                                 ServerWebExchange exchange,
                                                 String tenantId) {
        log.error("[TenantValidationFilter] tenant-service unavailable for tenantId=[{}]: {}",
                tenantId, ex.getMessage());
        return writeErrorResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                "gateway.tenant.service.unavailable");
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange,
                                           HttpStatus status,
                                           String messageKey) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");
        String body = buildErrorBody(messageKey, status.value());
        var buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    private String buildErrorBody(String messageKey, int httpStatus) {
        String message = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        return String.format(
                "{\"status\":%d,\"errorCode\":\"%s\",\"message\":\"%s\"}",
                httpStatus, messageKey, message);
    }

    private boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(pattern -> {
            String normalised = pattern.replace("/**", "");
            return path.startsWith(normalised);
        });
    }
}
