package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RegisterRequest — Application Layer DTO
 * Payload received on POST /api/v1/auth/register
 *
 * @author vamuhong
 * @version 2.0
 */
public record RegisterRequest(

        @NotBlank(message = "{auth.validation.firstname.required}")
        String firstname,

        @NotBlank(message = "{auth.validation.lastname.required}")
        String lastname,

        @NotBlank(message = "{auth.validation.email.required}")
        @Email(message = "{auth.validation.email.invalid}")
        String email,

        @NotBlank(message = "{auth.validation.password.required}")
        @Size(min = 8, message = "{auth.validation.password.length}")
        String password,

        /**
         * Tenant identifier — optional; defaults to "default" for single-tenant mode.
         * In SaaS mode, the tenant is derived from the subdomain or API key.
         */
        String tenantId,

        /**
         * Requested role — optional; defaults to USER.
         * Only USER and SELLER are accepted for public self-registration.
         * ADMIN cannot be self-registered.
         */
        String role
) {}
