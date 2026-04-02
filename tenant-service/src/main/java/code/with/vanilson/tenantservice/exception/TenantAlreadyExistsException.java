package code.with.vanilson.tenantservice.exception;

import org.springframework.http.HttpStatus;

/** HTTP 409 — slug or name already taken. */
public class TenantAlreadyExistsException extends TenantBaseException {
    public TenantAlreadyExistsException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }
}
