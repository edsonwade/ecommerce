package code.with.vanilson.orderservice;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * OrderResponse — Presentation Layer DTO
 * <p>
 * Immutable response record returned by the API.
 * Uses local PaymentMethod — no cross-service JAR dependencies.
 * Null/empty fields are excluded from JSON output (NON_EMPTY).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrderResponse(
        Integer id,
        String reference,
        BigDecimal amount,
        String paymentMethod,
        String customerId
) {
}
