package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * ForgotPasswordRequest — Application Layer DTO.
 * Payload received on POST /api/v1/auth/forgot-password.
 *
 * @author vamuhong
 * @version 1.0
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "{auth.validation.email.required}")
        @Email(message = "{auth.validation.email.invalid}")
        String email
) {}
