package code.with.vanilson.orderservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * OrderRepository — Infrastructure Layer (Phase 3 update)
 * Added findByCorrelationId for status polling endpoint.
 *
 * @author vamuhong
 * @version 3.0
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

    boolean existsByReference(String reference);

    /**
     * Finds an order by its client-facing correlationId.
     * Used internally by the Kafka saga consumer (OrderSagaConsumer.updateStatus)
     * where no tenant context is available — intentionally unscoped.
     * External callers MUST use {@link #findByCorrelationIdAndTenantId} instead.
     */
    Optional<Order> findByCorrelationId(String correlationId);

    /**
     * Tenant-scoped lookup by correlationId.
     * Use for all client-facing status polling to enforce tenant isolation at
     * the query level — defense-in-depth alongside the Hibernate filter.
     */
    Optional<Order> findByCorrelationIdAndTenantId(String correlationId, String tenantId);

    /**
     * Tenant-scoped lookup by primary key.
     * <p>
     * A by-id read via {@link #findById} resolves to {@code EntityManager.find}, which the
     * Hibernate {@code tenantFilter} does not apply to — so activating the filter cannot make
     * a by-id read tenant-safe. {@code OrderService.findById} uses this query when a tenant is
     * bound so a caller of tenant A reading tenant B's order by id gets an empty result (404)
     * rather than a cross-tenant leak; the pre-existing ownership guard checks the principal,
     * not the tenant, so it cannot close this gap on its own.
     */
    Optional<Order> findByOrderIdAndTenantId(Integer orderId, String tenantId);

    /** Returns all orders belonging to a specific customer. Hibernate tenant filter is active at call site. */
    java.util.List<Order> findByCustomerId(String customerId);

    /** Finds orders stuck in a specific status before the cutoff time. */
    java.util.List<Order> findByStatusAndCreatedDateBefore(OrderStatus status, java.time.LocalDateTime cutoff);
}
