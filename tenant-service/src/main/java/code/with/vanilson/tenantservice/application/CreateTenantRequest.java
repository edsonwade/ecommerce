package code.with.vanilson.tenantservice.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * CreateTenantRequest — Application Layer DTO
 * Received on POST /api/v1/tenants
 *
 * @author vamuhong
 * @version 4.0
 */
public record CreateTenantRequest(

        @NotBlank(message = "{tenant.name.required}")
        String name,

        /**
         * URL-friendly slug: lowercase letters, digits and hyphens only.
         * Used as a subdomain prefix: {slug}.vanilsonshop.io
         */
        @NotBlank(message = "{tenant.slug.required}")
        @Pattern(regexp = "^[a-z0-9][a-z0-9\\-]{1,98}[a-z0-9]$",
                 message = "{tenant.slug.invalid}")
        String slug,

        @NotBlank(message = "{tenant.contact.email.required}")
        @Email(message = "{tenant.contact.email.invalid}")
        String contactEmail,

        /** Optional initial plan — defaults to FREE. */
        String plan
) {}
