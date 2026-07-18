package code.with.vanilson.productservice.review;

/**
 * PurchaseVerificationResponse — product-service's own copy of the order-service
 * {@code /internal/purchases/exists} response body ({@code {"purchased": boolean}}).
 * Kept local (no shared JAR) so the two services stay decoupled, mirroring how order-service
 * owns its {@code CustomerInfo} for the customer-service call.
 *
 * @param purchased whether the customer has a fulfilled purchase of the product
 *
 * @author vamuhong
 * @version 1.0
 */
public record PurchaseVerificationResponse(boolean purchased) {
}
