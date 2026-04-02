package code.with.vanilson.tenantservice.domain;

/**
 * TenantStatus — Domain Enum
 * <p>
 * Lifecycle states of a tenant account.
 *
 * <pre>
 * ACTIVE     → Normal operation — all API calls accepted
 * SUSPENDED  → Account suspended (non-payment or policy violation) — API calls rejected
 * CANCELLED  → Account permanently closed — data scheduled for deletion
 * </pre>
 *
 * @author vamuhong
 * @version 4.0
 */
public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    CANCELLED
}
