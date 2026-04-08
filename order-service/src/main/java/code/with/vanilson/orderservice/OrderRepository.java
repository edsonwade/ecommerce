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
}
