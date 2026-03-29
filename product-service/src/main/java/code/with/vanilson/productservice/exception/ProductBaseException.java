package code.with.vanilson.productservice.exception;

import org.springframework.http.HttpStatus;

/**
 * ProductBaseException
 * <p>
 * Base exception for all Product Service exceptions.
 * All subclasses receive resolved messages from messages.properties via MessageSource.
 * Never pass hardcoded strings — always resolve via MessageSource first.
 * <p>
 * Liskov Substitution (SOLID-L): any ProductBaseException subtype can be used
 * wherever a ProductBaseException is expected without breaking callers.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public abstract class ProductBaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String messageKey;

    protected ProductBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey) {
        super(resolvedMessage);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    protected ProductBaseException(String resolvedMessage, HttpStatus httpStatus,
                                    String messageKey, Throwable cause) {
        super(resolvedMessage, cause);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
