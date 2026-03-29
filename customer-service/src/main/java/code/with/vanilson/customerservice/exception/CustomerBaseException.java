package code.with.vanilson.customerservice.exception;

import org.springframework.http.HttpStatus;

/**
 * CustomerBaseException
 * <p>
 * Base exception for all Customer Service exceptions.
 * Liskov Substitution (SOLID-L): all subtypes are safely substitutable.
 * All messages must be pre-resolved from messages.properties via MessageSource
 * before being passed to any subclass constructor.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public abstract class CustomerBaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String messageKey;

    protected CustomerBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey) {
        super(resolvedMessage);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    protected CustomerBaseException(String resolvedMessage, HttpStatus httpStatus,
                                     String messageKey, Throwable cause) {
        super(resolvedMessage, cause);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getMessageKey()     { return messageKey; }
}
