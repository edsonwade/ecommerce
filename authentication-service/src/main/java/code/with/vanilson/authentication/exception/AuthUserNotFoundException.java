package code.with.vanilson.authentication.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** HTTP 404 — user not found. Message key: auth.user.not.found */
public class AuthUserNotFoundException extends AuthBaseException {
    public AuthUserNotFoundException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.NOT_FOUND, messageKey);
    }
}
