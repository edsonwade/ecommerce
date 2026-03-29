package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/** HTTP 409 — email already registered. Message key: auth.user.already.exists */
public class UserAlreadyExistsException extends AuthBaseException {
    public UserAlreadyExistsException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }
}
