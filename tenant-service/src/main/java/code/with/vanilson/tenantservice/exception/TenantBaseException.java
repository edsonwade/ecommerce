package code.with.vanilson.tenantservice.exception;

import org.springframework.http.HttpStatus;

/**
 * TenantBaseException — base for all Tenant Service exceptions.
 * Messages resolved from messages.properties via MessageSource.
 *
 * @author vamuhong
 * @version 4.0
 */
public abstract class TenantBaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String     messageKey;

    protected TenantBaseException(String resolvedMessage, HttpStatus httpStatus, String messageKey) {
        super(resolvedMessage);
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String     getMessageKey() { return messageKey; }
}
