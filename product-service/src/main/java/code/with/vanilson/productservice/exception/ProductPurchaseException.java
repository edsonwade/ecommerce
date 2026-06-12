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

    private final Integer productId;
    private final String  productName;
    private final double  requestedQty;
    private final double  availableQty;

    public ProductPurchaseException(String resolvedMessage, String messageKey) {
        this(resolvedMessage, messageKey, null, null, 0, 0);
    }

    public ProductPurchaseException(String resolvedMessage, String messageKey,
                                    Integer productId, String productName,
                                    double requestedQty, double availableQty) {
        super(resolvedMessage, HttpStatus.UNPROCESSABLE_ENTITY, messageKey);
        this.productId = productId;
        this.productName = productName;
        this.requestedQty = requestedQty;
        this.availableQty = availableQty;
    }

    public Integer getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public double getRequestedQty() {
        return requestedQty;
    }

    public double getAvailableQty() {
        return availableQty;
    }
}
