package code.with.vanilson.tenantservice.exception;

import org.springframework.http.HttpStatus;

/** HTTP 403 — tenant is suspended or cancelled. */
public class TenantNotOperationalException extends TenantBaseException {
    public TenantNotOperationalException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.FORBIDDEN, messageKey);
    }
}
