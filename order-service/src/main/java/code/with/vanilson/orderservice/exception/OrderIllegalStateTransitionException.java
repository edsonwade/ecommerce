package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * OrderIllegalStateTransitionException
 * <p>
 * Thrown when an order status update violates the saga state machine
 * (e.g. CANCELLED → CONFIRMED). Guards against out-of-order or duplicate
 * Kafka events corrupting a terminal order state.
 * HTTP 409 Conflict.
 * Message key: order.status.transition.invalid
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
public class OrderIllegalStateTransitionException extends OrderBaseException {

    public OrderIllegalStateTransitionException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }
}
