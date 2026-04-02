package code.with.vanilson.tenantservice.application;

import jakarta.validation.constraints.NotBlank;

/**
 * SetFeatureFlagRequest — Application Layer DTO
 * Received on PUT /api/v1/tenants/{tenantId}/flags/{flagName}
 *
 * @author vamuhong
 * @version 4.0
 */
public record SetFeatureFlagRequest(
        boolean enabled,
        String  description
) {}
