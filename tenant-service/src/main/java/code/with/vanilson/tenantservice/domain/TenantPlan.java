package code.with.vanilson.tenantservice.domain;

/**
 * TenantPlan — Domain Enum
 * <p>
 * Defines the subscription tiers available in the SaaS platform.
 * Each plan has a rate limit (requests/minute) and a storage quota.
 * These values are also stored in the DB for runtime enforcement.
 *
 * <pre>
 * FREE       →   100 req/min,  1 GB
 * STARTER    → 1,000 req/min, 10 GB
 * GROWTH     →10,000 req/min,100 GB
 * ENTERPRISE → unlimited,    unlimited
 * </pre>
 *
 * @author vamuhong
 * @version 4.0
 */
public enum TenantPlan {

    FREE(100, 1_073_741_824L),          // 1 GB
    STARTER(1_000, 10_737_418_240L),    // 10 GB
    GROWTH(10_000, 107_374_182_400L),   // 100 GB
    ENTERPRISE(-1, -1L);                // -1 = unlimited

    private final int    requestsPerMinute;
    private final long   storageQuotaBytes;

    TenantPlan(int requestsPerMinute, long storageQuotaBytes) {
        this.requestsPerMinute = requestsPerMinute;
        this.storageQuotaBytes  = storageQuotaBytes;
    }

    public int  getRequestsPerMinute() { return requestsPerMinute; }
    public long getStorageQuotaBytes()  { return storageQuotaBytes; }

    public boolean isUnlimited() { return this == ENTERPRISE; }
}
