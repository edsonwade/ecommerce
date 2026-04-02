package code.with.vanilson.tenantcontext;

/**
 * TenantFilterConstants — Shared constants for Hibernate tenant filtering.
 * <p>
 * Every entity that participates in tenant isolation must declare:
 * <pre>
 * {@code @FilterDef(name = TenantFilterConstants.FILTER_NAME,
 *              parameters = @ParamDef(name = TenantFilterConstants.PARAM_NAME,
 *                                     type = String.class))}
 * {@code @Filter(name = TenantFilterConstants.FILTER_NAME,
 *         condition = "tenant_id = :" + TenantFilterConstants.PARAM_NAME)}
 * </pre>
 * <p>
 * The {@link TenantHibernateFilterActivator} enables this filter automatically
 * on every request that has a tenant context set.
 *
 * @author vamuhong
 * @version 4.0
 */
public final class TenantFilterConstants {

    /** Hibernate filter name — referenced by @FilterDef and @Filter. */
    public static final String FILTER_NAME = "tenantFilter";

    /** Hibernate filter parameter name — binds to tenant_id column. */
    public static final String PARAM_NAME = "tenantId";

    private TenantFilterConstants() {
        // Utility class — no instantiation
    }
}
