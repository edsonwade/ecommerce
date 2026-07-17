package code.with.vanilson.paymentservice.exception;

import org.springframework.http.HttpStatus;

/**
 * PaymentConflictException
 * <p>
 * General-purpose 409 for payment state conflicts (Fase 6: refunding an already-REFUNDED
 * payment). Distinct from {@link DuplicatePaymentException}, which is specifically the
 * idempotency-duplicate case on payment creation.
 *
 * @author vamuhong
 * @version 1.0
 */
public class PaymentConflictException extends PaymentBaseException {

    public PaymentConflictException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }
}
