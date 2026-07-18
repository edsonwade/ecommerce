package code.with.vanilson.productservice.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
