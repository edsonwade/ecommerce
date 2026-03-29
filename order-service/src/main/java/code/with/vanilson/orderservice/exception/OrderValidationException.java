package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * OrderValidationException
 * <p>
 * Thrown when order creation request fails business validation
 * (e.g. no products, invalid amount, missing customer).
 * HTTP 400 Bad Request.
 * Message keys: order.product.empty | order.amount.invalid | order.customer.id.required
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class OrderValidationException extends OrderBaseException {

    public OrderValidationException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.BAD_REQUEST, messageKey);
    }
}
