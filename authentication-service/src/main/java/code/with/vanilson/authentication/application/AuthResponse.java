package code.with.vanilson.authentication.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AuthResponse — Application Layer DTO
 * Returned on successful register, login, and token refresh.
 *
 * @author vamuhong
 * @version 2.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        String userId,
        String email,
        String role,
        String tenantId,
        String sellerStatus
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                   String userId, String email, String role, String tenantId) {
        return of(accessToken, refreshToken, userId, email, role, tenantId, null);
    }

    /**
     * sellerStatus is only present for SELLER accounts (PENDING_APPROVAL / APPROVED /
     * SUSPENDED) — null for other roles, and omitted from the JSON entirely (NON_NULL).
     */
    public static AuthResponse of(String accessToken, String refreshToken,
                                   String userId, String email, String role, String tenantId,
                                   String sellerStatus) {
        return new AuthResponse(accessToken, refreshToken, "Bearer",
                userId, email, role, tenantId, sellerStatus);
    }
}
