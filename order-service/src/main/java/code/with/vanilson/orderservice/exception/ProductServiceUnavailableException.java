package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * ProductServiceUnavailableException
 * <p>
 * Thrown when the Product Service is unreachable or returns an error during purchase.
 * HTTP 503 Service Unavailable.
 * Message key: order.product.service.unavailable | order.product.purchase.failed
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class ProductServiceUnavailableException extends OrderBaseException {

    public ProductServiceUnavailableException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey);
    }

    public ProductServiceUnavailableException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey, cause);
    }
}
