package code.with.vanilson.gatewayservice.client;

import reactor.core.publisher.Mono;

/**
 * TenantServiceClient
 * <p>
 * Abstraction over the HTTP call to the tenant-service validate endpoint.
 * Decouples TenantValidationFilter from the WebClient implementation,
 * enabling clean Mockito-based unit tests without starting an HTTP server.
 * <p>
 * Contract: returns empty Mono on 404, error signal on 4xx/5xx.
 *
 * @author vamuhong
 * @version 4.0
 */
public interface TenantServiceClient {

    /**
     * Calls GET /api/v1/tenants/{tenantId}/validate on the tenant-service.
     *
     * @param tenantId the tenant UUID from the JWT claim
     * @return Mono with TenantValidationResponse, or empty Mono if not found
     */
    Mono<TenantValidationResponse> validate(String tenantId);
}
