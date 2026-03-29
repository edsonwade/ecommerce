package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * OrderBaseException
 * <p>
 * Base exception for all Order Service exceptions.
 * All subclasses MUST receive resolved messages from messages.properties
 * via MessageSource — never pass hardcoded strings.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public abstract class OrderBaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String messageKey;

    protected OrderBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey) {
        super(resolvedMessage);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    protected OrderBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey, Throwable cause) {
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
