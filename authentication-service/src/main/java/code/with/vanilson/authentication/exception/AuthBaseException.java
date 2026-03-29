package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/**
 * AuthBaseException — base for all authentication service exceptions.
 * All resolved messages come from messages.properties via MessageSource.
 *
 * @author vamuhong
 * @version 2.0
 */
public abstract class AuthBaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String     messageKey;

    protected AuthBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey) {
        super(resolvedMessage);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    protected AuthBaseException(String resolvedMessage, HttpStatus httpStatus,
                                 String messageKey, Throwable cause) {
        super(resolvedMessage, cause);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String     getMessageKey() { return messageKey; }
}
