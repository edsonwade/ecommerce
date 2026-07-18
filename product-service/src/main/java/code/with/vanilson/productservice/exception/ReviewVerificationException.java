package code.with.vanilson.productservice.exception;

import org.springframework.http.HttpStatus;

/**
 * ReviewVerificationException — the purchase-verification dependency (order-service) is
 * unavailable, so a review write cannot be safely accepted. HTTP 503 (B1 fail-closed): we never
 * store an unverified review. Extends {@link ProductBaseException}, so it flows through the
 * {@code handleProductBase} catch-all with its resolved message + key.
 *
 * @author vamuhong
 * @version 1.0
 */
public class ReviewVerificationException extends ProductBaseException {

    public ReviewVerificationException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey);
    }

    public ReviewVerificationException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey, cause);
    }
}
