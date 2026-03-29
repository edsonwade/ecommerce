package code.with.vanilson.cartservice.exception;

import org.springframework.http.HttpStatus;

/**
 * CartBaseException — base for all Cart Service exceptions.
 * Messages always resolved from messages.properties.
 *
 * @author vamuhong
 * @version 2.0
 */
public abstract class CartBaseException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String     messageKey;

    protected CartBaseException(String msg, HttpStatus status, String key) {
        super(msg); this.httpStatus = status; this.messageKey = key;
    }

    protected CartBaseException(String msg, HttpStatus status, String key, Throwable cause) {
        super(msg, cause); this.httpStatus = status; this.messageKey = key;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String     getMessageKey() { return messageKey; }
}
