package code.with.vanilson.tenantcontext.security;

/**
 * Claims extracted from a validated JWT.
 * <p>
 * sellerStatus is nullable — only present on tokens issued for SELLER accounts
 * (PENDING_APPROVAL / APPROVED / SUSPENDED). Tokens minted before the seller
 * approval flow existed carry no such claim and parse to null (old-token compat).
 * </p>
 */
public record JwtClaims(String subject, Long userId, String tenantId, String role,
                        String sellerStatus) {

    /** Pre-seller-approval shape — kept so existing call sites stay source-compatible. */
    public JwtClaims(String subject, Long userId, String tenantId, String role) {
        this(subject, userId, tenantId, role, null);
    }
}
