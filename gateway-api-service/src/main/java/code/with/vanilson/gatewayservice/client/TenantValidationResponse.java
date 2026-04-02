package code.with.vanilson.gatewayservice.client;

/**
 * TenantValidationResponse
 * <p>
 * Projection of the tenant-service TenantResponse used exclusively by the Gateway.
 * Only the fields required for gateway enforcement are mapped here.
 * <p>
 * Fields:
 * - tenantId   : UUID string identifying the tenant
 * - status     : ACTIVE | SUSPENDED | CANCELLED
 * - rateLimit  : requests-per-minute tier limit (used to scope Redis rate-limit bucket)
 *
 * @author vamuhong
 * @version 4.0
 */
public record TenantValidationResponse(
        String tenantId,
        String status,
        int    rateLimit
) {}
