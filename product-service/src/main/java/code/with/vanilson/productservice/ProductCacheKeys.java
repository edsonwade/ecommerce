package code.with.vanilson.productservice;

/**
 * ProductCacheKeys — single source of truth for the product cache names and key layout.
 * <p>
 * Introduced in Fase 7 Task 7.3. Before it, the tenant discriminator lived only inside
 * {@link ProductService#cacheTenantKey()} and the cache names were private constants there. Task 7.3
 * needs {@code ReviewService} to evict a product's cached detail after a review changes its rating
 * counters, and an evict only works when it reproduces the producer's key <em>byte for byte</em>.
 * <p>
 * That is a genuinely easy thing to get wrong here: {@code ReviewService} already has a
 * {@code currentTenant()} helper that falls back to {@code "default"}, while the cache key falls back
 * to {@code "none"} — reusing the wrong one would compute a key that never matches, so the evict
 * would silently no-op and the catalogue would keep serving a stale average. Centralising the layout
 * makes that mismatch impossible rather than merely unlikely.
 *
 * @author vamuhong
 * @version 1.0
 */
public final class ProductCacheKeys {

    /** Per-product detail cache ({@code products::{tenant}:{id}}). */
    public static final String CACHE_PRODUCTS = "products";

    /** Paginated catalogue cache. Deliberately NOT evicted on review writes — see Decision A1 (no-evict). */
    public static final String CACHE_PRODUCT_LIST = "product-list";

    private ProductCacheKeys() {
        // utility holder
    }

    /**
     * Tenant discriminator: keeps one tenant's cached entry from being served to another under a
     * shared, tenant-blind key.
     *
     * @param tenantId the bound tenant, may be null/blank in single-tenant dev
     * @return the tenant id, or {@code "none"} when no tenant is bound
     */
    public static String tenantKey(String tenantId) {
        return (tenantId == null || tenantId.isBlank()) ? "none" : tenantId;
    }

    /**
     * The exact key {@link ProductService#getProductById(int)} caches a product detail under.
     *
     * @param tenantId  the bound tenant (may be null/blank)
     * @param productId the product id
     * @return {@code "{tenant}:{id}"}
     */
    public static String detailKey(String tenantId, int productId) {
        return tenantKey(tenantId) + ":" + productId;
    }
}
