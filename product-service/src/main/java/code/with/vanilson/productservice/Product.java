package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.tenantcontext.TenantFilterConstants;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;

@NoArgsConstructor
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

    public Product(Integer id, String name, String description, double availableQuantity, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
    }

    public Product(String name, String description, double availableQuantity, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
    }

    public Product(Integer id, String name, String description, double availableQuantity, BigDecimal price,
                   Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
        this.category = category;
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
    }

}