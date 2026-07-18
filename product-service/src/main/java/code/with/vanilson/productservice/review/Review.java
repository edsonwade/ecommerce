package code.with.vanilson.productservice.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Review — Domain Entity (F7).
 * <p>
 * A single product review: a 1–5 star {@code rating} plus an optional {@code comment}, written by a
 * customer who has actually bought the product (verified out-of-band via order-service). One review
 * per {@code (productId, customerId)} — enforced by the DB unique constraint (migration V13).
 * <p>
 * Tenancy: {@code tenantId} is stamped on the row (NOT NULL) from the {@code TenantContext}, mirroring
 * {@link code.with.vanilson.productservice.category.Category}. Reads are keyed by {@code productId},
 * and a product belongs to exactly one tenant, so the query is already tenant-correct without a
 * Hibernate {@code @Filter} — and {@code tenantId} is deliberately OUT of the unique key (T1) so the
 * duplicate guard cannot be weakened by an inconsistent tenant value.
 *
 * @author vamuhong
 * @version 1.0
 */
@Entity
@Table(name = "product_review",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_review_customer_product", columnNames = {"product_id", "customer_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", length = 2000)
    private String comment;

    /** SaaS tenant — stamped from TenantContext on creation, never updated. */
    @Column(name = "tenant_id", nullable = false, length = 50, updatable = false)
    private String tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
