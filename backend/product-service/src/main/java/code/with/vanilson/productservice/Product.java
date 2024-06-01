package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.Category;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Builder
@Getter
@Setter
@Entity
@Table(name = "product")
@JsonPropertyOrder(value = {"id", "name", "description", "availableQuantity", "price", "category"})
public class Product implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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

    public Product(Integer id, String name, String description, double availableQuantity, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
    }

    public Product( String name, String description, double availableQuantity, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.availableQuantity = availableQuantity;
        this.price = price;
    }
}