package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** AccountResponse — the authenticated user's own identity view (never another user's). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountResponse(
        Long id,
        String firstname,
        String lastname,
        String email,
        String role,
        LocalDateTime createdAt,
        // Live seller-approval status straight from the DB (null for non-sellers). The SPA polls
        // this endpoint while a seller is PENDING_APPROVAL so approval/suspension are reflected
        // without a re-login: on a change it silently refreshes the token (which re-reads this
        // same status) and unlocks or locks the UI accordingly.
        String sellerStatus
) {
    public static AccountResponse from(User user) {
        return new AccountResponse(user.getId(), user.getFirstname(), user.getLastname(),
                user.getEmail(), user.getRole().name(), user.getCreatedAt(),
                user.getSellerStatus() != null ? user.getSellerStatus().name() : null);
    }
}
