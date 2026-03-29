package code.with.vanilson.gatewayservice.exception;

import org.springframework.http.HttpStatus;

/**
 * JwtAuthenticationException
 * <p>
 * Thrown when a JWT token is missing, expired, malformed, or has an invalid signature.
 * HTTP 401 Unauthorized.
 * Message key: gateway.auth.invalid.token (or specific variant)
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class JwtAuthenticationException extends GatewayBaseException {

    public JwtAuthenticationException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.UNAUTHORIZED, messageKey);
    }

    public JwtAuthenticationException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.UNAUTHORIZED, messageKey, cause);
    }
}
