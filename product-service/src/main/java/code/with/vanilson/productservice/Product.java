package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.tenantcontext.TenantFilterConstants;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;

@AllArgsConstructor
@EqualsAndHashCode
@Builder
@Getter
@Setter
@Entity
@Table(name = "product")
@FilterDef(
        name = TenantFilterConstants.FILTER_NAME,
        parameters = @ParamDef(name = TenantFilterConstants.PARAM_NAME, type = String.class)
)
@Filter(
        name = TenantFilterConstants.FILTER_NAME,
        condition = "tenant_id = :" + TenantFilterConstants.PARAM_NAME
)
@JsonPropertyOrder(value = {"id", "name", "description", "availableQuantity", "price", "category", "tenantId"})
public class Product {

    @Id
    @GeneratedValue
    private Integer id;
    private String name;
    private String description;
    private double availableQuantity;
    private BigDecimal price;
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * Phase 4: Tenant isolation — every product belongs to exactly one tenant.
     * Populated automatically from TenantContext; filtered via Hibernate @Filter.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /** Phase 4 RBAC: userId of the SELLER/ADMIN who created this product. */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    /** Phase 4 RBAC: userId of the last user who updated this product. Null until first update. */
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Fase 3: lifecycle status. SUSPENDED products are hidden from public reads and
     * rejected on the purchase path; ACTIVE behaves exactly as before this field existed.
     * Java default mirrors the DB default in {@code V12__add_status_to_product.sql} so a
     * newly built entity is born ACTIVE regardless of which constructor created it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    /**
     * Fase 7 (Task 7.3, Decision A1): denormalised review counters.
     * <p>
     * The catalogue renders stars straight off these two columns — zero query cost on every
     * list/search/detail read, instead of aggregating {@code product_review} per page. They are
     * NOT a cache: {@code ReviewService} recomputes them from source (COUNT/AVG over
     * {@code product_review}) inside the same transaction as each review write/delete, so they
     * are race-safe and self-healing. Never null — a product with no reviews reads 0.0 / 0,
     * mirroring the {@code NOT NULL DEFAULT 0} in {@code V14__add_rating_counters_to_product.sql}.
     */
    @Column(name = "average_rating", nullable = false, precision = 2, scale = 1)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private int reviewCount = 0;

    // Fase 3: Lombok's @Builder.Default moves the field initializer into the builder
    // ONLY — it is stripped from every constructor, so without setting it here each
    // hand-written constructor (and the no-args one JPA uses) would leave status null,
    // then fail the NOT NULL column on save. Every constructor therefore stamps ACTIVE
    // explicitly so a product is genuinely "born ACTIVE regardless of how it was built".
    // Fase 7 (7.3): averageRating has exactly the same trap — it is a BigDecimal, so a
    // stripped initializer leaves it null against a NOT NULL column. reviewCount is a
    // primitive int and falls back to 0 on its own, so only averageRating needs stamping.
    public Product() {
        this.status = ProductStatus.ACTIVE;
        this.averageRating = BigDecimal.ZERO;
    }

    public Product(Integer id, String name, String description, double availableQuantity, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
        this.status = ProductStatus.ACTIVE;
        this.averageRating = BigDecimal.ZERO;
    }

    public Product(String name, String description, double availableQuantity, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
        this.status = ProductStatus.ACTIVE;
        this.averageRating = BigDecimal.ZERO;
    }

    public Product(Integer id, String name, String description, double availableQuantity, BigDecimal price,
                   Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
        this.category = category;
        this.status = ProductStatus.ACTIVE;
        this.averageRating = BigDecimal.ZERO;
    }

    public Product(Integer id, String name, String description, double availableQuantity, BigDecimal price,
                   Category category, String tenantId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
        this.category = category;
        this.tenantId = tenantId;
        this.status = ProductStatus.ACTIVE;
        this.averageRating = BigDecimal.ZERO;
    }

}