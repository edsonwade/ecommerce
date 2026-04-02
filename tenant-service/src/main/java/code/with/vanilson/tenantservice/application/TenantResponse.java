package code.with.vanilson.tenantservice.application;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * TenantResponse — Application Layer DTO
 * <p>
 * Returned by all tenant read/write operations.
 * Exposes only safe fields — never exposes internal DB id (Long).
 *
 * @author vamuhong
 * @version 4.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantResponse(
        String        tenantId,
        String        name,
        String        slug,
        String        contactEmail,
        String        plan,
        String        status,
        int           rateLimit,
        long          storageQuota,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
