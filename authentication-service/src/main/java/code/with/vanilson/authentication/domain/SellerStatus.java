package code.with.vanilson.authentication.domain;

/**
 * SellerStatus — Domain Enum
 * <p>
 * Lifecycle of a SELLER account's marketplace approval (null for non-sellers):
 * <p>
 * PENDING_APPROVAL → self-registered seller awaiting admin review (cannot write products)
 * APPROVED         → may manage their catalog (admin-created sellers are born APPROVED)
 * SUSPENDED        → writes blocked by admin action; refresh tokens revoked on suspension
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public enum SellerStatus {
    PENDING_APPROVAL,
    APPROVED,
    SUSPENDED
}
