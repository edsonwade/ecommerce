package code.with.vanilson.customerservice.exception;

import org.springframework.http.HttpStatus;

/**
 * CustomerNotFoundException
 * HTTP 404 Not Found.
 * Message keys: customer.not.found.by.id | customer.not.found.by.email
 *
 * @author vamuhong
 * @version 2.0
 */
public class CustomerNotFoundException extends CustomerBaseException {

    public CustomerNotFoundException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.NOT_FOUND, messageKey);
    }
}
