package code.with.vanilson.productservice.exception;

import org.springframework.http.HttpStatus;

/**
 * ProductNullException
 * <p>
 * Thrown when a required product field is null or an invalid value is provided.
 * HTTP 400 Bad Request.
 * Message keys: product.null | product.name.null | product.price.null | product.quantity.negative
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class ProductNullException extends ProductBaseException {


    public ProductNullException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.BAD_REQUEST, messageKey);
    }
}
