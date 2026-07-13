package code.with.vanilson.productservice.category;

import code.with.vanilson.productservice.Product;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue
    private Integer id;
    private String name;
    private String description;
    @OneToMany(mappedBy = "category", cascade = CascadeType.REMOVE)
    @JsonProperty("products")
    private List<Product> products;

    /**
     * Fase 4: {@code category.tenant_id} is {@code NOT NULL} (migration V4). Categories are
     * seeded via Flyway with a tenant tag; a category created at runtime must stamp one too
     * or the INSERT fails the constraint. Reads are intentionally NOT tenant-filtered (the
     * catalogue dropdown lists every category), mirroring {@code Product.tenantId}'s stamp.
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    public Category(Integer id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }
}