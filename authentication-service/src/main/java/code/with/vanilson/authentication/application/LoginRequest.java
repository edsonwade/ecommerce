package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * LoginRequest — Application Layer DTO
 * Payload received on POST /api/v1/auth/login
 *
 * @author vamuhong
 * @version 2.0
 */
public record LoginRequest(

        @NotBlank(message = "{auth.validation.email.required}")
        @Email(message = "{auth.validation.email.invalid}")
        String email,

        @NotBlank(message = "{auth.validation.password.required}")
        String password
) {}
