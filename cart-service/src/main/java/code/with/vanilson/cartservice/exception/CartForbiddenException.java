package code.with.vanilson.cartservice.exception;

import org.springframework.http.HttpStatus;

public class CartForbiddenException extends CartBaseException {
    public CartForbiddenException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.FORBIDDEN, messageKey);
    }
}
