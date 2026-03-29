package code.with.vanilson.cartservice.exception;

import org.springframework.http.HttpStatus;

/** HTTP 404 — cart or cart item not found. */
public class CartNotFoundException extends CartBaseException {
    public CartNotFoundException(String msg, String key) {
        super(msg, HttpStatus.NOT_FOUND, key);
    }
}
