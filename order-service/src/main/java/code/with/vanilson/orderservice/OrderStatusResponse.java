package code.with.vanilson.orderservice;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderStatusResponse — Presentation Layer DTO
 * Returned by GET /api/v1/orders/status/{correlationId}
 *
 * @author vamuhong
 * @version 3.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderStatusResponse(
        Integer       orderId,
        String        correlationId,
        String        reference,
        String        status,
        BigDecimal    totalAmount,
        LocalDateTime createdDate
) {}
