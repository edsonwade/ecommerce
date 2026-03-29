package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * OrderDuplicateReferenceException
 * <p>
 * Thrown when an order with the same reference already exists.
 * Prevents duplicate order submissions (idempotency guard at service layer).
 * HTTP 409 Conflict.
 * Message key: order.reference.duplicate
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class OrderDuplicateReferenceException extends OrderBaseException {

    public OrderDuplicateReferenceException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }
}
