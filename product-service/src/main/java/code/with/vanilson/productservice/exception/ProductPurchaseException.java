package code.with.vanilson.productservice.exception;

import org.springframework.http.HttpStatus;

/**
 * ProductPurchaseException
 * <p>
 * Thrown when a product purchase fails — insufficient stock or product not found.
 * HTTP 422 Unprocessable Entity (not 400 — request was valid but business rule failed).
 * Message keys: product.purchase.insufficient.stock | product.purchase.not.found
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class ProductPurchaseException extends ProductBaseException {

    public ProductPurchaseException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.UNPROCESSABLE_ENTITY, messageKey);
    }
}
