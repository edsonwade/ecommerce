package code.with.vanilson.customerservice.exception;

import org.springframework.http.HttpStatus;

public class CustomerForbiddenException extends CustomerBaseException {

    public CustomerForbiddenException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.FORBIDDEN, messageKey);
    }
}
