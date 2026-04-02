package code.with.vanilson.tenantservice.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * UpdateTenantRequest — Application Layer DTO
 * Received on PUT /api/v1/tenants/{tenantId}
 * Slug is immutable — excluded from updates.
 *
 * @author vamuhong
 * @version 4.0
 */
public record UpdateTenantRequest(

        @NotBlank(message = "{tenant.name.required}")
        String name,

        @NotBlank(message = "{tenant.contact.email.required}")
        @Email(message = "{tenant.contact.email.invalid}")
        String contactEmail
) {}
