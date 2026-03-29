package code.with.vanilson.paymentservice.exception;

import org.springframework.http.HttpStatus;

/**
 * PaymentNotFoundException
 * <p>
 * Thrown when a payment cannot be found by ID or order reference.
 * HTTP 404 Not Found.
 * Message keys: payment.not.found | payment.order.not.found
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class PaymentNotFoundException extends PaymentBaseException {

    public PaymentNotFoundException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.NOT_FOUND, messageKey);
    }
}
