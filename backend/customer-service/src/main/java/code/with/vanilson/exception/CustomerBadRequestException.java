package code.with.vanilson.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CustomerBadRequestException extends RuntimeException {
    public CustomerBadRequestException(String message) {
        super(message);
    }
}
