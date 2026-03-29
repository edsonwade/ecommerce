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
        String tenantId
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                   String userId, String email, String role, String tenantId) {
        return new AuthResponse(accessToken, refreshToken, "Bearer",
                userId, email, role, tenantId);
    }
}
