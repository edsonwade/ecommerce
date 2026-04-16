package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.tenantcontext.TenantFilterConstants;
import jakarta.persistence.Column;
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
import org.hibernate.annotations.Filter;

/**
 * OrderLine — Domain Entity (Phase 4 update)
 * <p>
 * Represents a single line item in an order (one product + quantity).
 * Maps to the customer_line table.
 * <p>
 * Phase 4: tenantId + Hibernate @Filter for per-tenant data isolation.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "customer_line")
@Filter(name = TenantFilterConstants.FILTER_NAME,
        condition = "tenant_id = :" + TenantFilterConstants.PARAM_NAME)
public class OrderLine {

    @Id
    @GeneratedValue
    private Integer id;

    /** SaaS tenant UUID — set from TenantContext on creation, never updated. */
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    private Integer productId;

    private double quantity;
}
