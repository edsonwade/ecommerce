package code.with.vanilson.tenantservice.application;

import code.with.vanilson.tenantservice.domain.TenantFeatureFlag;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * FeatureFlagResponse — Application Layer DTO
 *
 * @author vamuhong
 * @version 4.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureFlagResponse(
        Long          id,
        String        tenantId,
        String        flagName,
        boolean       enabled,
        String        description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FeatureFlagResponse from(TenantFeatureFlag flag) {
        return new FeatureFlagResponse(
                flag.getId(),
                flag.getTenantId(),
                flag.getFlagName(),
                flag.isEnabled(),
                flag.getDescription(),
                flag.getCreatedAt(),
                flag.getUpdatedAt()
        );
    }
}
