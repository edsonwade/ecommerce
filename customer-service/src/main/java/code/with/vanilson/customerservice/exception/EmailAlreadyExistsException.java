package code.with.vanilson.customerservice.exception;

import org.springframework.http.HttpStatus;

/**
 * EmailAlreadyExistsException
 * HTTP 409 Conflict.
 * Message key: customer.email.already.exists
 *
 * @author vamuhong
 * @version 2.0
 */
public class EmailAlreadyExistsException extends CustomerBaseException {

    public EmailAlreadyExistsException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.CONFLICT, messageKey);
    }
}
