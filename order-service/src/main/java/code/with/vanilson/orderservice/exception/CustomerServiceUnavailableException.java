package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * CustomerServiceUnavailableException
 * <p>
 * Thrown when the Customer Service is unreachable or the circuit breaker is open.
 * HTTP 503 Service Unavailable.
 * Message key: order.customer.service.unavailable
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class CustomerServiceUnavailableException extends OrderBaseException {

    public CustomerServiceUnavailableException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey);
    }

    public CustomerServiceUnavailableException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey, cause);
    }
}
