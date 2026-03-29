package code.with.vanilson.paymentservice.exception;

import org.springframework.http.HttpStatus;

/**
 * PaymentValidationException
 * <p>
 * Thrown when the payment request fails business validation
 * (negative amount, missing method, missing order ID).
 * HTTP 400 Bad Request.
 * Message keys: payment.amount.invalid | payment.method.required | payment.order.id.required
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class PaymentValidationException extends PaymentBaseException {

    public PaymentValidationException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.BAD_REQUEST, messageKey);
    }
}
