package code.with.vanilson.orderservice.exception;

import org.springframework.http.HttpStatus;

public class OrderForbiddenException extends OrderBaseException {
    public OrderForbiddenException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.FORBIDDEN, messageKey);
    }
}
