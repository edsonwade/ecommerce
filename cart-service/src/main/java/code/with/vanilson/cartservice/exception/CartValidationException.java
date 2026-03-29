package code.with.vanilson.cartservice.exception;

import org.springframework.http.HttpStatus;

/** HTTP 400 — invalid cart operation (empty quantity, empty checkout, etc.). */
public class CartValidationException extends CartBaseException {
    public CartValidationException(String msg, String key) {
        super(msg, HttpStatus.BAD_REQUEST, key);
    }
}
