package code.with.vanilson.gatewayservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * TenantServiceClientImpl
 * <p>
 * WebClient-based implementation of TenantServiceClient.
 * Calls the tenant-service validate endpoint reactively.
 * <p>
 * Error handling:
 * - HTTP 404            → Mono.empty() (caller treats as unknown tenant)
 * - HTTP 403            → propagates as WebClientResponseException (tenant suspended/cancelled)
 * - Timeout / 5xx / IO  → fail-open with synthetic ACTIVE response so the gateway does
 *                         not return 503 for every request when tenant-service is down.
 * <p>
 * Base URL resolved from ${gateway.tenant-service.base-url} (e.g. lb://tenant-service).
 *
 * @author vamuhong
 * @version 4.1
 */
@Slf4j
@Component
public class TenantServiceClientImpl implements TenantServiceClient {

    private static final String VALIDATE_PATH = "/api/v1/tenants/{tenantId}/validate";
    private static final Duration VALIDATION_TIMEOUT = Duration.ofSeconds(3);
    private static final int FAIL_OPEN_RATE_LIMIT = 1000;

    private final WebClient webClient;

    public TenantServiceClientImpl(
            WebClient.Builder webClientBuilder,
            @Value("${gateway.tenant-service.base-url:lb://tenant-service}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public Mono<TenantValidationResponse> validate(String tenantId) {
        return webClient.get()
                .uri(VALIDATE_PATH, tenantId)
                .retrieve()
                // Let WebClient throw WebClientResponseException.NotFound for 404;
                // the onErrorResume below converts it to Mono.empty() so the filter
                // hits switchIfEmpty → handleTenantNotFound (404 to caller).
                // Do NOT suppress 404 here with Mono.empty() — that causes WebClient to
                // deserialize the error body as TenantValidationResponse, yielding
                // status="404" which the filter misreads as a cancelled tenant (→ 403).
                .bodyToMono(TenantValidationResponse.class)
                .timeout(VALIDATION_TIMEOUT)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("[TenantServiceClient] Tenant not found: tenantId=[{}]", tenantId);
                    return Mono.empty();
                })
                .onErrorResume(WebClientResponseException.Forbidden.class, Mono::error)
                .onErrorResume(TimeoutException.class, ex -> failOpen(tenantId, "timeout"))
                .onErrorResume(WebClientRequestException.class, ex -> failOpen(tenantId, "connection-error"))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().is5xxServerError()) {
                        return failOpen(tenantId, "tenant-service-5xx");
                    }
                    return Mono.error(ex);
                });
    }

    /**
     * When tenant-service is unreachable or slow, fail-open with a synthetic ACTIVE tenant
     * so cart/orders/products keep working. The alternative (503 for every request) is worse.
     */
    private Mono<TenantValidationResponse> failOpen(String tenantId, String reason) {
        log.warn("[TenantServiceClient] FAIL-OPEN tenantId=[{}] reason=[{}] — synthetic ACTIVE response issued",
                tenantId, reason);
        return Mono.just(new TenantValidationResponse(tenantId, "ACTIVE", FAIL_OPEN_RATE_LIMIT));
    }
}
