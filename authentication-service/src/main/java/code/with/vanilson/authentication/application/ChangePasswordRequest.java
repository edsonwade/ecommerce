package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ChangePasswordRequest — payload for POST /api/v1/auth/account/change-password. */
public record ChangePasswordRequest(
        @NotBlank(message = "{auth.validation.password.required}")
        String currentPassword,

        @NotBlank(message = "{auth.validation.password.required}")
        @Size(min = 8, message = "{auth.validation.password.length}")
        String newPassword,

        @NotBlank(message = "{auth.validation.password.required}")
        String confirmPassword
) {}
