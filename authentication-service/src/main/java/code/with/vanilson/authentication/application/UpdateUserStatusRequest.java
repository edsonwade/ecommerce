package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.NotNull;

/**
 * UpdateUserStatusRequest — Application Layer DTO
 * Payload received on PATCH /api/v1/auth/users/{userId}/status (ADMIN only).
 * {@code enabled=false} deactivates the account and revokes every session;
 * {@code enabled=true} reactivates it.
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "{auth.validation.enabled.required}")
        Boolean enabled
) {}
