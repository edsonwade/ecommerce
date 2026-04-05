package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/**
 * InvalidTokenException — thrown when a JWT token is malformed, has an invalid signature,
 * or is used in an incorrect context (e.g., access token used as refresh token).
 * Maps to HTTP 401 UNAUTHORIZED.
 *
 * @author vamuhong
 * @version 2.0
 */
public class InvalidTokenException extends AuthBaseException {

    public InvalidTokenException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.UNAUTHORIZED, messageKey);
    }
}
