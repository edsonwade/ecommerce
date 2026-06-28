package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/**
 * InvalidPasswordResetTokenException — thrown when a password-reset token is unknown, expired,
 * already used, or the supplied passwords do not match. Maps to HTTP 400 BAD REQUEST.
 * <p>
 * Deliberately generic so a client cannot distinguish "wrong token" from "expired token" —
 * the same as not revealing whether an email exists.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
public class InvalidPasswordResetTokenException extends AuthBaseException {

    public InvalidPasswordResetTokenException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.BAD_REQUEST, messageKey);
    }
}
