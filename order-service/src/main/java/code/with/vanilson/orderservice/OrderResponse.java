package code.with.vanilson.orderservice;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderResponse — Presentation Layer DTO
 * <p>
 * Immutable response record returned by the API. Invoice-grade: besides the order header
 * (reference, status, payment, total) it carries the customer block (name, email, shipping
 * address), the creation timestamp, and the money breakdown (subtotal, discount, promotion,
 * IVA/tax). Null/empty fields are excluded from JSON output ({@code NON_EMPTY}), so legacy
 * orders and customers without an address on file simply omit those keys.
 * <p>
 * A backward-compatible 6-arg constructor is retained so existing call-sites (and tests)
 * that only build the header keep compiling — the invoice fields default to {@code null}.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrderResponse(
        Integer id,
        String reference,
        BigDecimal amount,
        String paymentMethod,
        String customerId,
        String status,

        // ── Customer block ──
        String customerFirstname,
        String customerLastname,
        String customerEmail,

        // ── Shipping address ──
        String shippingStreet,
        String shippingHouseNumber,
        String shippingZipCode,
        String shippingCity,
        String shippingCountry,

        // ── Order header ──
        LocalDateTime createdDate,

        // ── Money breakdown ──
        BigDecimal subtotal,
        BigDecimal discountAmount,
        String promotionCode,
        BigDecimal promotionAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount
) {
    /**
     * Backward-compatible header-only constructor (the original v2 shape).
     * Invoice fields are left null and therefore omitted from JSON (NON_EMPTY).
     */
    public OrderResponse(Integer id, String reference, BigDecimal amount,
                         String paymentMethod, String customerId, String status) {
        this(id, reference, amount, paymentMethod, customerId, status,
                null, null, null,
                null, null, null, null, null,
                null,
                null, null, null, null, null, null);
    }
}
