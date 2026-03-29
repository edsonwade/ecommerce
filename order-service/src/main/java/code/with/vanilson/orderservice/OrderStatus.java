package code.with.vanilson.orderservice;

/**
 * OrderStatus — Domain Enum
 * <p>
 * Represents the lifecycle of an order in the Choreography Saga.
 * <p>
 * State machine:
 * REQUESTED → INVENTORY_RESERVED → PAYMENT_AUTHORIZED → CONFIRMED
 *           ↘ INVENTORY_INSUFFICIENT → CANCELLED
 *                                    ↘ PAYMENT_FAILED → CANCELLED
 * <p>
 * PENDING_PAYMENT: payment-service was unreachable — order is persisted,
 * payment retried from DLQ when payment-service recovers.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
public enum OrderStatus {

    /** Event published to Kafka, awaiting inventory reservation. */
    REQUESTED,

    /** Inventory successfully reserved in product-service. */
    INVENTORY_RESERVED,

    /** Product stock was insufficient — order cancelled. */
    INVENTORY_INSUFFICIENT,

    /** Payment authorised — waiting for final confirmation. */
    PAYMENT_AUTHORIZED,

    /** Payment failed — inventory will be released, order cancelled. */
    PAYMENT_FAILED,

    /** Order fully confirmed — email sent to customer. */
    CONFIRMED,

    /** Order cancelled (inventory insufficient or payment failed). */
    CANCELLED,

    /** Payment-service was unreachable — retry pending from DLQ. */
    PENDING_PAYMENT
}
