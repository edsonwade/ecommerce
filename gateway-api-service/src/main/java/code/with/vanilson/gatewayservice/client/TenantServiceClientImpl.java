package code.with.vanilson.gatewayservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * TenantServiceClientImpl
 * <p>
 * WebClient-based implementation of TenantServiceClient.
 * Calls the tenant-service validate endpoint reactively.
 * <p>
 * Error handling:
 * - HTTP 404  → returns Mono.empty() (caller treats as unknown tenant)
 * - HTTP 403  → propagates as WebClientResponseException (tenant suspended/cancelled)
 * - Other 4xx/5xx → propagates as WebClientResponseException
 * <p>
 * Base URL resolved from ${gateway.tenant-service.base-url} (e.g. lb://tenant-service).
 * Uses Eureka load balancer when prefixed with lb://.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@Component
public class TenantServiceClientImpl implements TenantServiceClient {

    private static final String VALIDATE_PATH = "/api/v1/tenants/{tenantId}/validate";

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
                .onStatus(
                        status -> status == HttpStatus.NOT_FOUND,
                        response -> Mono.empty()
                )
                .bodyToMono(TenantValidationResponse.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("[TenantServiceClient] Tenant not found: tenantId=[{}]", tenantId);
                    return Mono.empty();
                })
                .doOnError(ex -> log.error(
                        "[TenantServiceClient] Error validating tenant=[{}]: {}",
                        tenantId, ex.getMessage()));
    }
}
