package code.with.vanilson.productservice.exception;

import org.springframework.http.HttpStatus;

/**
 * ProductConflictException
 * <p>
 * Thrown when a write would violate a uniqueness or referential-integrity rule that
 * the client can resolve — e.g. creating a category whose name already exists, or
 * deleting a category still referenced by products. HTTP 409 Conflict.
 * <p>
 * Extends {@link ProductBaseException} so it flows through the {@code handleProductBase}
 * catch-all in {@code ProductGlobalExceptionHandler}, carrying its resolved message and
 * message key (no hardcoded strings, never a stack trace to the client).
 * <p>
 * Liskov Substitution (SOLID-L): usable anywhere a {@link ProductBaseException} is expected.
 *
 * @author vamuhong
 * @version 2.0
 */
public class ProductConflictException extends ProductBaseException {

    public ProductConflictException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }

    public ProductConflictException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey, cause);
    }
}
