package code.with.vanilson.paymentservice.exception;

import org.springframework.http.HttpStatus;

/**
 * PaymentBaseException
 * <p>
 * Base exception for all Payment Service exceptions.
 * All subclasses receive resolved messages from messages.properties via MessageSource.
 * Never pass hardcoded strings — always resolve via MessageSource first.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public abstract class PaymentBaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String messageKey;

    protected PaymentBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey) {
        super(resolvedMessage);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    protected PaymentBaseException(String resolvedMessage, HttpStatus httpStatus,
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
