package code.with.vanilson.paymentservice.domain;

/**
 * PaymentStatus — Fase 6 (basic refunds).
 * <p>
 * {@code AUTHORIZED} is the only status a payment is created with (existing rows are
 * grandfathered to it — migration {@code V1.4}). {@code REFUNDED} is terminal: a payment
 * can be refunded exactly once ({@code PaymentService.refundPayment} rejects a second
 * attempt with 409).
 *
 * @author vamuhong
 * @version 1.0
 */
public enum PaymentStatus {
    AUTHORIZED,
    REFUNDED
}
