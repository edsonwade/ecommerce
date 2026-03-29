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
     * Used by the status polling endpoint: GET /orders/status/{correlationId}
     */
    Optional<Order> findByCorrelationId(String correlationId);
}
