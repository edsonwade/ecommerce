package code.with.vanilson.paymentservice.exception;

import org.springframework.http.HttpStatus;

public class PaymentForbiddenException extends PaymentBaseException {
    public PaymentForbiddenException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.FORBIDDEN, messageKey);
    }
}
