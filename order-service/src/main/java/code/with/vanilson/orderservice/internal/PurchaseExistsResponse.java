package code.with.vanilson.orderservice.internal;

/**
 * PurchaseExistsResponse — internal S2S response body.
 * <p>
 * Serialised as {@code {"purchased": boolean}}. Returned by
 * {@link InternalPurchaseController#hasPurchased} to product-service so it can
 * gate a review write on a real, fulfilled purchase. Intentionally minimal — it
 * carries no order/customer detail across the trust boundary, only the yes/no fact.
 * </p>
 *
 * @param purchased {@code true} iff the customer has at least one fulfilled order
 *                  line (CONFIRMED/SHIPPED/DELIVERED) for the product.
 *
 * @author vamuhong
 * @version 1.0
 */
public record PurchaseExistsResponse(boolean purchased) {
}
