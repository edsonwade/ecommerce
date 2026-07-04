package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.NotBlank;

/** DeleteAccountRequest — payload for DELETE /api/v1/auth/account/me (password re-auth). */
public record DeleteAccountRequest(
        @NotBlank(message = "{auth.validation.password.required}")
        String password
) {}
