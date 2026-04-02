package code.with.vanilson.tenantcontext;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Filter;
import org.hibernate.Session;

/**
 * TenantHibernateFilterActivator — Infrastructure Layer
 * <p>
 * Enables the Hibernate {@code tenantFilter} on the current JPA session,
 * binding the tenant ID from {@link TenantContext}.
 * <p>
 * Called by {@link TenantInterceptor} after setting the tenant context.
 * Any entity annotated with {@code @FilterDef / @Filter} for
 * {@link TenantFilterConstants#FILTER_NAME} will automatically have
 * {@code WHERE tenant_id = :tenantId} appended to all queries.
 * <p>
 * This approach is opt-in per entity: only entities with the annotation
 * participate in automatic filtering. Entities without it (e.g. Tenant
 * itself) are accessible without restriction.
 * <p>
 * <strong>Note:</strong> This class is NOT a {@code @Component}. It is registered
 * conditionally by {@link TenantContextAutoConfiguration.HibernateTenantFilterConfig}
 * only when JPA is on the classpath. Services without JPA (e.g. cart-service with Redis)
 * safely skip this bean.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
public class TenantHibernateFilterActivator {

    private final EntityManager entityManager;

    public TenantHibernateFilterActivator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Activates the Hibernate tenant filter for the current session.
     * Safe to call even if no tenant context is set — simply does nothing.
     * <p>
     * Must be called within an active transaction / request scope.
     */
    public void activateFilter() {
        if (!TenantContext.isPresent()) {
            log.trace("No tenant context — Hibernate filter not activated");
            return;
        }

        String tenantId = TenantContext.getCurrentTenantId();
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter(TenantFilterConstants.FILTER_NAME);
        filter.setParameter(TenantFilterConstants.PARAM_NAME, tenantId);

        log.debug("Hibernate tenantFilter activated: tenantId=[{}]", tenantId);
    }

    /**
     * Disables the Hibernate tenant filter for the current session.
     * Useful for admin operations that need cross-tenant visibility.
     */
    public void deactivateFilter() {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter(TenantFilterConstants.FILTER_NAME);
        log.debug("Hibernate tenantFilter deactivated");
    }
}
