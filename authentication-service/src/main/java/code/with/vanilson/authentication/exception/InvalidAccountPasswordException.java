package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/** HTTP 400 — the re-auth password on an account-settings operation is wrong or missing. */
public class InvalidAccountPasswordException extends AuthBaseException {
    public InvalidAccountPasswordException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.BAD_REQUEST, messageKey);
    }
}
