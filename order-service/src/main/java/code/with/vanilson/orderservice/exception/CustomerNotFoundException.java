package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * CustomerNotFoundException
 * <p>
 * Thrown when the referenced customer does not exist in customer-service.
 * HTTP 404 Not Found.
 * Message key: order.customer.not.found
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
public class CustomerNotFoundException extends OrderBaseException {

    public CustomerNotFoundException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.NOT_FOUND, messageKey);
    }
}
