package code.with.vanilson.productservice.exception;

import org.springframework.http.HttpStatus;

/**
 * ProductNotFoundException
 * <p>
 * Thrown when a product cannot be located by ID.
 * HTTP 404 Not Found.
 * Message key: product.not.found
 * <p>
 * CHANGED FROM ORIGINAL: extends ProductBaseException (not RuntimeException directly).
 * Messages are resolved from messages.properties — no hardcoded strings.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class ProductNotFoundException extends ProductBaseException {

    public ProductNotFoundException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.NOT_FOUND, messageKey);
    }

    public ProductNotFoundException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.NOT_FOUND, messageKey, cause);
    }
}
