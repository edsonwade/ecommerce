package code.with.vanilson.orderservice.payment;

/**
 * PaymentMethod
 * <p>
 * Local enum owned exclusively by order-service.
 * Duplicated intentionally — each service owns its domain types.
 * No shared enums between services via JAR dependencies.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public enum PaymentMethod {
    PAYPAL,
    CREDIT_CARD,
    VISA,
    MASTER_CARD,
    BITCOIN
}
