package code.with.vanilson.productservice.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ReviewRepository — Infrastructure Layer (F7).
 *
 * @author vamuhong
 * @version 1.0
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Backs the one-review-per-customer guard on POST (→ 409 when already present). */
    boolean existsByProductIdAndCustomerId(Integer productId, Long customerId);

    /**
     * Paginated reviews for a product. Sort/paging come from the {@link Pageable}
     * (default newest-first, set on the controller). The catalogue never calls this for
     * star counts — those live denormalised on the product (A1).
     */
    Page<Review> findByProductId(Integer productId, Pageable pageable);

    /**
     * The caller's own review of a product, if any. Backs the eligibility endpoint (7.4a), which needs
     * the review itself — not just its existence — so the UI can show what was already written.
     */
    Optional<Review> findByProductIdAndCustomerId(Integer productId, Long customerId);

    /**
     * Every review in the tenant, newest-first by default, for ADMIN moderation (7.4a).
     * <p>
     * Joins {@code Product} to carry the product name into the projection, so a moderation page costs
     * one statement instead of one lookup per row. {@code Review} has no JPA association to
     * {@code Product} (it stores a plain {@code productId}), hence the explicit {@code ON} join.
     * <p>
     * Scoping is by explicit {@code tenantId} parameter rather than a Hibernate filter — the same
     * reason seller isolation keys off an explicit column: relying on an implicit filter for a
     * cross-entity read is how leaks happen.
     */
    @Query(value = """
            SELECT new code.with.vanilson.productservice.review.AdminReviewResponse(
                       r.id, r.productId, p.name, r.customerId, r.rating, r.comment, r.createdAt)
            FROM Review r
            JOIN Product p ON p.id = r.productId
            WHERE r.tenantId = :tenantId
            """,
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.tenantId = :tenantId")
    Page<AdminReviewResponse> findAllForModeration(@Param("tenantId") String tenantId, Pageable pageable);
}
