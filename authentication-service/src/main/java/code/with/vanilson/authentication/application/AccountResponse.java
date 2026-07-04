package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.User;

import java.time.LocalDateTime;

/** AccountResponse — the authenticated user's own identity view (never another user's). */
public record AccountResponse(
        Long id,
        String firstname,
        String lastname,
        String email,
        String role,
        LocalDateTime createdAt
) {
    public static AccountResponse from(User user) {
        return new AccountResponse(user.getId(), user.getFirstname(), user.getLastname(),
                user.getEmail(), user.getRole().name(), user.getCreatedAt());
    }
}
