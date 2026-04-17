package code.with.vanilson.productservice.exception;

import org.springframework.http.HttpStatus;

public class ProductForbiddenException extends ProductBaseException {

    public ProductForbiddenException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.FORBIDDEN, messageKey);
    }
}
