package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;

/** HTTP 401 — token expired or revoked. Message key: auth.token.revoked | auth.jwt.expired */
public class TokenRevokedException extends AuthBaseException {
    public TokenRevokedException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.UNAUTHORIZED, messageKey);
    }
}
