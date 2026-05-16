package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/** HTTP 400 — invalid registration attempt (disallowed role, invalid role value). */
public class RegistrationException extends AuthBaseException {
    public RegistrationException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.BAD_REQUEST, messageKey);
    }
}
