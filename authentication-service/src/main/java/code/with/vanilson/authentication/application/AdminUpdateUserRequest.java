package code.with.vanilson.authentication.application;

import jakarta.validation.constraints.Email;

/**
 * AdminUpdateUserRequest — Application Layer DTO
 * Payload received on PATCH /api/v1/auth/users/{userId} (ADMIN only).
 * <p>
 * Partial update: every field is optional, but at least one must be provided
 * (enforced in {@code UserManagementService.updateUser}). An email change revokes
 * the target's sessions — the JWT subject is the email, so old tokens are dead anyway.
 * </p>
 */
public record AdminUpdateUserRequest(

        String firstname,

        String lastname,

        @Email(message = "{auth.validation.email.invalid}")
        String email
) {}
