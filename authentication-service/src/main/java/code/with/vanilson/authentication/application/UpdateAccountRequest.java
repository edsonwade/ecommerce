package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * UpdateAccountRequest — payload for PATCH /api/v1/auth/account/me.
 * currentPassword is only required when the email is being changed (checked in the service,
 * not here, because "required" depends on whether the email actually differs).
 */
public record UpdateAccountRequest(
        @NotBlank(message = "{auth.validation.firstname.required}")
        String firstname,

        @NotBlank(message = "{auth.validation.lastname.required}")
        String lastname,

        @NotBlank(message = "{auth.validation.email.required}")
        @Email(message = "{auth.validation.email.invalid}")
        String email,

        String currentPassword
) {}
