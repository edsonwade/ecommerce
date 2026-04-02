package code.with.vanilson.tenantservice.application;

import code.with.vanilson.tenantservice.domain.Tenant;
import org.springframework.stereotype.Component;

/**
 * TenantMapper — Application Layer
 * <p>
 * Maps between Tenant entity and request/response DTOs.
 * Single Responsibility (SOLID-S): pure mapping, no business logic.
 *
 * @author vamuhong
 * @version 4.0
 */
@Component
public class TenantMapper {

    /**
     * Maps a Tenant entity to a TenantResponse DTO.
     * Returns null if tenant is null — callers must guard.
     *
     * @param tenant the persisted Tenant entity
     * @return TenantResponse DTO
     */
    public TenantResponse toResponse(Tenant tenant) {
        if (tenant == null) return null;
        return new TenantResponse(
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getContactEmail(),
                tenant.getPlan().name(),
                tenant.getStatus().name(),
                tenant.getRateLimit(),
                tenant.getStorageQuota(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
