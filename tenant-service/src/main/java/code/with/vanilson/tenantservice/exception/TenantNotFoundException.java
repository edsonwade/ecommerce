package code.with.vanilson.tenantservice.exception;

import org.springframework.http.HttpStatus;

/** HTTP 404 — tenant not found by ID or slug. */
public class TenantNotFoundException extends TenantBaseException {
    public TenantNotFoundException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.NOT_FOUND, messageKey);
    }
}
