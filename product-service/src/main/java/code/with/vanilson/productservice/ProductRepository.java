package code.with.vanilson.productservice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.util.List;

/**
 * ProductRepository — Infrastructure Layer
 * <p>
 * JPA repository for the Product entity.
 * <p>
 * KEY CHANGE — Pessimistic locking on findAllByIdInOrderById:
 * PESSIMISTIC_WRITE acquires a SELECT FOR UPDATE lock, preventing two concurrent
 * order transactions from both reading the same stock level and both succeeding
 * (the classic race condition on inventory). With this lock:
 * - Transaction A reads product P with qty=1
 * - Transaction B tries to read the same product P → BLOCKED until A commits
 * - A decrements to 0 and commits
 * - B reads qty=0 → throws ProductPurchaseException (insufficient stock)
 * This eliminates overselling without any application-level synchronisation.
 * <p>
 * Trade-off: Higher lock contention under extreme concurrency (flash sales).
 * Phase 3 mitigation: move to event-driven saga with optimistic concurrency
 * and compensating transactions for oversell scenarios.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {

    /**
     * Fetches products by IDs in ascending order, applying a pessimistic write lock.
     * Used exclusively by purchaseProducts() to prevent race conditions on stock.
     *
     * @param ids list of product IDs to fetch
     * @return list of products sorted by ID (ascending) with pessimistic lock held
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Product> findAllByIdInOrderById(@Param("ids") List<Integer> ids);
}
