package code.with.vanilson.paymentservice.application;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentResponse — Application Layer DTO
 * <p>
 * Immutable response returned to the caller after payment creation.
 * Uses String for paymentMethod to decouple presentation from domain enum.
 * NON_EMPTY excludes null/empty fields from JSON output.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PaymentResponse(
        Integer paymentId,
        BigDecimal amount,
        String paymentMethod,
        Integer orderId,
        String orderReference,
        LocalDateTime createdDate
) {
}
