package code.with.vanilson.paymentservice.exception;

import org.springframework.http.HttpStatus;

/**
 * DuplicatePaymentException
 * <p>
 * Thrown when a payment for the same orderReference already exists
 * AND the idempotency guard detects a true duplicate (not a retry returning cached result).
 * This should rarely be thrown — normally idempotency returns the existing payment silently.
 * HTTP 409 Conflict.
 * Message key: payment.duplicate.idempotency
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class DuplicatePaymentException extends PaymentBaseException {

    public DuplicatePaymentException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }
}
