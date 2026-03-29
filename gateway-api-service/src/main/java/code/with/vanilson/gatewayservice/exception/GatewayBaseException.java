package code.with.vanilson.gatewayservice.exception;

import org.springframework.http.HttpStatus;

/**
 * GatewayBaseException
 * <p>
 * Base exception for all Gateway exceptions.
 * All messages MUST be loaded from messages.properties via MessageSource.
 * Never pass hardcoded strings to this constructor from production code.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public abstract class GatewayBaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String messageKey;

    protected GatewayBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey) {
        super(resolvedMessage);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    protected GatewayBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey, Throwable cause) {
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
