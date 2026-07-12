package code.with.vanilson.tenantcontext.security;

/**
 * Authenticated principal placed in the SecurityContext by {@link JwtAuthenticationFilter}.
 * <p>
 * sellerStatus is nullable — non-null only for SELLER accounts whose token carries the
 * claim (PENDING_APPROVAL / APPROVED / SUSPENDED). A null value means either a non-seller
 * or a token minted before the seller approval flow existed; callers must treat null as
 * "no restriction" (grandfathered sellers keep working until their token rotates).
 * </p>
 */
public record SecurityPrincipal(String email, Long userId, String tenantId, String role,
                                String sellerStatus) {

    /** Pre-seller-approval shape — kept so existing call sites stay source-compatible. */
    public SecurityPrincipal(String email, Long userId, String tenantId, String role) {
        this(email, userId, tenantId, role, null);
    }

    public boolean isAdmin()  { return "ADMIN".equals(role); }
    public boolean isSeller() { return "SELLER".equals(role); }
    public boolean isUser()   { return "USER".equals(role); }
}
