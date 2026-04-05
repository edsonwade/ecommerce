package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/**
 * TokenExpiredException — thrown when a JWT token has expired.
 * Maps to HTTP 401 UNAUTHORIZED.
 *
 * @author vamuhong
 * @version 2.0
 */
public class TokenExpiredException extends AuthBaseException {

    public TokenExpiredException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.UNAUTHORIZED, messageKey);
    }
}
