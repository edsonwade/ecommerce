package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * OrderInternalServiceException
 * <p>
 * Represent internal service errors that are expected to be caught by the global handler.
 * Used to avoid logging full stack traces for known simulated errors in tests.
 * </p>
 */
public class OrderInternalServiceException extends OrderBaseException {

    public OrderInternalServiceException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.INTERNAL_SERVER_ERROR, messageKey);
    }

    public OrderInternalServiceException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, cause);
    }
}
