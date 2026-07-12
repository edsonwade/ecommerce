package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 400 — an ADMIN attempted a self-targeting user-management action that is not allowed
 * (self-deactivation, self-deletion). Message keys: auth.admin.self.deactivate.denied,
 * auth.admin.self.delete.denied.
 */
public class AdminActionNotAllowedException extends AuthBaseException {
    public AdminActionNotAllowedException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.BAD_REQUEST, messageKey);
    }
}
