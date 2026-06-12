package code.with.vanilson.tenantservice.exception;

import org.springframework.http.HttpStatus;

/** HTTP 409 — tenant is not CANCELLED, so permanent deletion is not allowed. */
public class TenantDeletionNotAllowedException extends TenantBaseException {
    public TenantDeletionNotAllowedException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }
}
