package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * PaymentServiceUnavailableException
 * <p>
 * Thrown when the Payment Service is unreachable or the circuit breaker is open.
 * In this case the order is persisted in PENDING state and payment is retried asynchronously.
 * HTTP 503 Service Unavailable.
 * Message key: order.payment.service.unavailable
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class PaymentServiceUnavailableException extends OrderBaseException {

    public PaymentServiceUnavailableException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey);
    }

    public PaymentServiceUnavailableException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey, cause);
    }
}
