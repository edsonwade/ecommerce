package code.with.vanilson.cartservice.application;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CartResponse — Application Layer DTO
 * Returned by all cart read operations.
 *
 * @author vamuhong
 * @version 2.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartResponse(
        String        cartId,
        String        customerId,
        List<CartItemResponse> items,
        BigDecimal    total,
        int           itemCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** Nested DTO for individual line items. */
    public record CartItemResponse(
            Integer    productId,
            String     productName,
            String     productDescription,
            BigDecimal unitPrice,
            double     quantity,
            BigDecimal lineTotal
    ) {}
}
