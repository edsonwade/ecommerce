package code.with.vanilson.paymentservice.domain;

/**
 * PaymentMethod — Domain Enum
 * <p>
 * Local enum owned exclusively by payment-service.
 * Intentionally duplicated from order-service — each service owns its domain types.
 * No shared enums across service JARs (violates service independence).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public enum PaymentMethod {
    PAYPAL,
    CREDIT_CARD,
    DEBIT_CARD,
    VISA,
    MASTER_CARD,
    BITCOIN
}
