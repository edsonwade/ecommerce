package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AdminCreateUserRequest — Application Layer DTO
 * Payload received on POST /api/v1/auth/users (ADMIN only).
 * <p>
 * Unlike public self-registration ({@link RegisterRequest}), an ADMIN may assign
 * any role — including ADMIN. The public /register endpoint keeps blocking ADMIN.
 * </p>
 */
public record AdminCreateUserRequest(

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

        @NotNull(message = "{auth.validation.role.required}")
        Role role,

        /**
         * Tenant identifier — optional; defaults to "default" (the catalogue's tenant).
         */
        String tenantId
) {}
