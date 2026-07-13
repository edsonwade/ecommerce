package code.with.vanilson.productservice;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Returns only the products created by the given user (the seller's own catalogue).
     * <p>
     * Backs {@code GET /api/v1/products/mine}: in this marketplace, competing sellers
     * must not see each other's products. Ownership is keyed off {@code created_by}
     * (the seller's userId, stamped at creation) — not {@code tenant_id}, which the
     * read path does not currently filter on. The public catalogue ({@code /products},
     * {@code /products/search}) intentionally stays cross-seller so customers browse
     * every store.
     *
     * @param createdBy the seller's userId (as stored in {@code created_by})
     * @param pageable  pagination and sort parameters
     * @return page of the seller's own products
     */
    Page<Product> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * Tenant-scoped lookup by primary key.
     * <p>
     * A by-id read via {@code findById} resolves to {@code EntityManager.find}, which
     * Hibernate {@code @Filter} does not apply to — so the tenant filter alone cannot make
     * a by-id read tenant-safe. {@code getProductById} uses this query when a tenant is
     * bound so a caller of tenant A reading tenant B's product by id gets an empty result
     * (404), not a cross-tenant leak. Defense-in-depth at the query level alongside the
     * Hibernate filter used by the list reads.
     *
     * @param id       product primary key
     * @param tenantId the tenant the caller is bound to
     * @return the product only if it belongs to {@code tenantId}, otherwise empty
     */
    java.util.Optional<Product> findByIdAndTenantId(Integer id, String tenantId);

    /**
     * Fase 3 (D4 — explicit predicate): the public catalogue lists only products in the
     * given status (in practice {@code ACTIVE}). Derived query — unlike {@code em.find},
     * the Hibernate tenant {@code @Filter} applies to it, so tenant isolation on the list
     * path is preserved. Backed by {@code idx_product_status} (migration V12).
     *
     * @param status   the lifecycle status to filter by
     * @param pageable pagination and sort parameters
     * @return page of products in the given status
     */
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /**
     * Fase 4: counts products referencing a category. Backs the category delete-guard —
     * a category still referenced by any product must not be deleted (would orphan the FK
     * or, with cascade, delete live products), so the service returns 409 when this is &gt; 0.
     * Traverses {@code Product.category.id}.
     *
     * @param categoryId the category primary key
     * @return number of products pointing at that category
     */
    long countByCategoryId(Integer categoryId);
}
