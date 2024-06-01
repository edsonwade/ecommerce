package code.with.vanilson.productservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ProductNullException extends RuntimeException {
    public ProductNullException(String message) {
        super(message);
    }
}