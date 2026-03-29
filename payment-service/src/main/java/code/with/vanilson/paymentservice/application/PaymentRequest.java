package code.with.vanilson.paymentservice.application;

import code.with.vanilson.paymentservice.domain.CustomerData;
import code.with.vanilson.paymentservice.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * PaymentRequest — Application Layer DTO
 * <p>
 * Validated request received from order-service via HTTP POST.
 * Uses local domain types (CustomerData, PaymentMethod) — no cross-service JAR imports.
 * <p>
 * CHANGED FROM ORIGINAL:
 * - CustomerData replaces imported Customer from customer-service JAR
 * - PaymentMethod is now local domain enum
 * - Added @NotBlank on orderReference (was missing — required for idempotency key)
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record PaymentRequest(
        Integer id,

        @NotNull(message = "{payment.validation.amount.positive}")
        @Positive(message = "{payment.validation.amount.positive}")
        BigDecimal amount,

        @NotNull(message = "{payment.validation.method.required}")
        PaymentMethod paymentMethod,

        @NotNull(message = "{payment.validation.order.required}")
        Integer orderId,

        @NotBlank(message = "{payment.validation.reference.required}")
        String orderReference,

        @NotNull(message = "{payment.validation.customer.required}")
        @Valid
        CustomerData customer
) {
}
