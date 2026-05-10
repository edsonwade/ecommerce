package code.with.vanilson.cartservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * CartItem — Domain Value Object
 * <p>
 * Represents a single product line in a cart.
 * Embedded within Cart (not a separate Redis hash).
 * <p>
 * lineTotal = unitPrice × quantity (computed, not stored — recalculated on read).
 * unitPrice is snapshotted at add-to-cart time. At checkout, prices are re-validated
 * against the Pricing Service — stale price protection per the architecture plan.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {

    private Integer    productId;
    private String     productName;
    private String     productDescription;

    /** Price snapshotted at add-to-cart time — validated again at checkout. */
    private BigDecimal unitPrice;

    private double     quantity;

    /** Stock snapshotted at add-to-cart time — used by frontend to cap + button. */
    private Integer    availableQuantity;

    /** Computed field — not stored, recalculated on read. */
    public BigDecimal getLineTotal() {
        if (unitPrice == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
