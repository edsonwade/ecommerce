package code.with.vanilson.tenantservice.exception;

import org.springframework.http.HttpStatus;

public class TenantForbiddenException extends TenantBaseException {
    public TenantForbiddenException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.FORBIDDEN, messageKey);
    }
}
