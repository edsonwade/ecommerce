package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.orderservice.Order;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OrderLine — Domain Entity
 * <p>
 * Represents a single line item in an order (one product + quantity).
 * Maps to the customer_line table.
 * <p>
 * Clean Architecture: this is a pure domain entity — no HTTP, no Kafka, no external deps.
 * SOLID-S: owns only its persistence state, nothing else.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "customer_line")
public class OrderLine {

    @Id
    @GeneratedValue
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    private Integer productId;

    private double quantity;
}
