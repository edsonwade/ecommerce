package code.with.vanilson.tenantcontext;

import code.with.vanilson.tenantcontext.exception.MissingTenantException;

/**
 * TenantContext — Core Multi-Tenancy Foundation
 * <p>
 * Thread-safe holder for the current tenant ID during a request lifecycle.
 * Uses {@link InheritableThreadLocal} so child threads (e.g. @Async tasks)
 * inherit the tenant context from the parent thread.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>TenantInterceptor extracts X-Tenant-ID from the HTTP header</li>
 *   <li>TenantInterceptor calls {@link #setCurrentTenantId(String)}</li>
 *   <li>All downstream code reads via {@link #getCurrentTenantId()}</li>
 *   <li>TenantInterceptor calls {@link #clear()} in afterCompletion</li>
 * </ol>
 * <p>
 * Every service that depends on tenant-context gets automatic tenant isolation
 * without touching a single line of business code.
 *
 * @author vamuhong
 * @version 4.0
 */
public final class TenantContext {

    /** HTTP header name propagated by Gateway and Feign interceptors. */
    public static final String TENANT_HEADER = "X-Tenant-ID";

    private static final InheritableThreadLocal<String> CURRENT_TENANT =
            new InheritableThreadLocal<>();

    private TenantContext() {
        // Utility class — no instantiation
    }

    /**
     * Returns the tenant ID bound to the current thread.
     *
     * @return the tenant ID, or {@code null} if not set
     */
    public static String getCurrentTenantId() {
        return CURRENT_TENANT.get();
    }

    /**
     * Binds the given tenant ID to the current thread.
     *
     * @param tenantId the UUID tenant identifier extracted from X-Tenant-ID
     */
    public static void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Removes the tenant ID from the current thread.
     * Must be called in {@code afterCompletion} to prevent memory leaks in pooled threads.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Returns {@code true} if a tenant ID is present on the current thread.
     */
    public static boolean isPresent() {
        return CURRENT_TENANT.get() != null && !CURRENT_TENANT.get().isBlank();
    }

    /**
     * Returns the current tenant ID or throws {@link MissingTenantException}
     * if none is set. Use this in code paths that absolutely require a tenant.
     *
     * @return the tenant ID, never null
     * @throws MissingTenantException if tenant ID is absent
     */
    public static String requireCurrentTenantId() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new MissingTenantException();
        }
        return tenantId;
    }
}
