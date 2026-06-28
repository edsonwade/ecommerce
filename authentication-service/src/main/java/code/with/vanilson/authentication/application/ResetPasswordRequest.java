package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ResetPasswordRequest — Application Layer DTO.
 * Payload received on POST /api/v1/auth/reset-password. {@code confirmPassword} is validated
 * server-side (not just in the browser) — equality is enforced in PasswordResetService.
 *
 * @author vamuhong
 * @version 1.0
 */
public record ResetPasswordRequest(

        @NotBlank(message = "{auth.validation.reset.token.required}")
        String token,

        @NotBlank(message = "{auth.validation.password.required}")
        @Size(min = 8, message = "{auth.validation.password.length}")
        String newPassword,

        @NotBlank(message = "{auth.validation.password.required}")
        String confirmPassword
) {}
