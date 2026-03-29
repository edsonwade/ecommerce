package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * OrderNotFoundException
 * <p>
 * Thrown when an order cannot be found by ID or reference.
 * HTTP 404 Not Found.
 * Message keys: order.not.found | order.reference.not.found
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class OrderNotFoundException extends OrderBaseException {

    public OrderNotFoundException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.NOT_FOUND, messageKey);
    }
}
