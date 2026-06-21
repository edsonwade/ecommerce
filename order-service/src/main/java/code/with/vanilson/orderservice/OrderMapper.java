package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.customer.CustomerSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * OrderMapper — Application Layer (invoice update)
 * <p>
 * Maps Order entity ↔ request/response DTOs and builds the invoice-grade response:
 * customer block (from the local {@link CustomerSnapshot}) + money breakdown.
 * <p>
 * Pricing model: displayed prices are tax-inclusive (Portugal/IVA). When an order has no
 * persisted breakdown (all legacy orders today) the subtotal and IVA are derived from the
 * authoritative {@code totalAmount} and the configured rate — no invented numbers:
 * {@code subtotal = total / (1 + rate)}, {@code tax = total − subtotal}. Discount and
 * promotion are truthfully absent (0/null) until checkout captures them.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@Component
public class OrderMapper {

    /** IVA rate used to split a tax-inclusive total when the order has no persisted rate. */
    private final BigDecimal defaultTaxRate;

    public OrderMapper(@Value("${application.invoice.tax-rate:0.23}") BigDecimal defaultTaxRate) {
        this.defaultTaxRate = defaultTaxRate;
    }

    /**
     * Maps an OrderRequest to an Order entity.
     * correlationId and status are intentionally NOT set here —
     * they are set by OrderService.persistOrderWithLines() before calling save().
     */
    public Order toOrder(OrderRequest request) {
        if (request == null) return null;
        return Order.builder()
                .orderId(request.id())
                .reference(request.reference())
                .totalAmount(request.amount())
                .paymentMethod(request.paymentMethod())
                .customerId(request.customerId())
                .status(OrderStatus.REQUESTED)
                .build();
    }

    /**
     * Header-only mapping (no customer enrichment). Used where the snapshot isn't loaded.
     * Still computes the money breakdown + createdDate so totals are invoice-correct.
     */
    public OrderResponse fromOrder(Order order) {
        return fromOrder(order, null);
    }

    /**
     * Full invoice mapping. {@code snapshot} may be null (customer not yet in the local read
     * model) — the customer block is then simply omitted from the JSON (NON_EMPTY).
     */
    public OrderResponse fromOrder(Order order, CustomerSnapshot snapshot) {
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal rate  = order.getTaxRate() != null ? order.getTaxRate() : defaultTaxRate;

        BigDecimal subtotal = order.getSubtotal();
        BigDecimal taxAmount = order.getTaxAmount();
        if (subtotal == null || taxAmount == null) {
            // Tax-inclusive split of the authoritative total.
            subtotal  = total.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
            taxAmount = total.subtract(subtotal);
        }

        BigDecimal discount    = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal promoAmount = order.getPromotionAmount() != null ? order.getPromotionAmount() : BigDecimal.ZERO;

        return new OrderResponse(
                order.getOrderId(),
                order.getReference(),
                total,
                order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null,
                order.getCustomerId(),
                order.getStatus() != null ? order.getStatus().name() : null,
                snapshot != null ? snapshot.getFirstname() : null,
                snapshot != null ? snapshot.getLastname() : null,
                snapshot != null ? snapshot.getEmail() : null,
                snapshot != null ? snapshot.getStreet() : null,
                snapshot != null ? snapshot.getHouseNumber() : null,
                snapshot != null ? snapshot.getZipCode() : null,
                snapshot != null ? snapshot.getCity() : null,
                snapshot != null ? snapshot.getCountry() : null,
                order.getCreatedDate(),
                subtotal,
                discount,
                order.getPromotionCode(),
                promoAmount,
                rate,
                taxAmount
        );
    }
}
