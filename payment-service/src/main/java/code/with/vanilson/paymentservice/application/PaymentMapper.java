package code.with.vanilson.paymentservice.application;

import code.with.vanilson.paymentservice.domain.Payment;
import org.springframework.stereotype.Component;

/**
 * PaymentMapper — Application Layer
 * <p>
 * Maps between PaymentRequest DTO → Payment domain entity, and
 * Payment entity → PaymentResponse DTO.
 * <p>
 * Single Responsibility (SOLID-S): only mapping logic here.
 * Open/Closed (SOLID-O): extend by adding new mapping methods — never modify existing ones.
 * <p>
 * NOTE: idempotencyKey is NOT set here — it is set by PaymentService
 * before calling the mapper, following the Single Responsibility principle.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Component
public class PaymentMapper {

    /**
     * Maps a PaymentRequest DTO to a Payment entity.
     * The idempotencyKey field is intentionally left null here —
     * PaymentService sets it before persisting.
     *
     * @param request validated payment request
     * @return Payment entity ready for idempotencyKey assignment and persistence
     */
    public Payment toPayment(PaymentRequest request) {
        if (request == null) {
            return null;
        }
        return Payment.builder()
                .paymentId(request.id())
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .orderId(request.orderId())
                .orderReference(request.orderReference())
                .build();
    }

    /**
     * Maps a Payment entity to a PaymentResponse DTO.
     * paymentMethod serialised to String to avoid coupling presentation to domain enum.
     *
     * @param payment persisted Payment entity
     * @return PaymentResponse DTO for the HTTP response body
     */
    public PaymentResponse toResponse(Payment payment) {
        if (payment == null) {
            return null;
        }
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null,
                payment.getOrderId(),
                payment.getOrderReference(),
                payment.getCreatedDate()
        );
    }
}
