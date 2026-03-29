package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/** HTTP 401 — invalid credentials. Message key: auth.login.invalid.credentials */
public class InvalidCredentialsException extends AuthBaseException {
    public InvalidCredentialsException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.UNAUTHORIZED, messageKey);
    }
}
