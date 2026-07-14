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
 * <p>
 * Fase 5 (fulfillment) extends the machine PAST confirmation. CONFIRMED is no longer
 * terminal — a seller/admin advances it manually:
 * CONFIRMED → SHIPPED → DELIVERED, and any of CONFIRMED/SHIPPED/DELIVERED → REFUNDED
 * (refund arrives via the saga in Fase 6). REFUNDED and CANCELLED are terminal.
 * The saga path (Kafka) still only ever drives an order up to CONFIRMED/CANCELLED;
 * SHIPPED/DELIVERED are reachable ONLY through the manual PATCH endpoint (Task 5.2).
 * </p>
 *
 * @author vamuhong
 * @version 4.0
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
    PENDING_PAYMENT,

    /** Saga took too long to complete — order cancelled. */
    TIMEOUT,

    /** Fulfillment: seller/admin marked the confirmed order as shipped. */
    SHIPPED,

    /** Fulfillment: order delivered to the customer. */
    DELIVERED,

    /** Order refunded (Fase 6, arrives via the payment saga). Terminal. */
    REFUNDED;

    /**
     * Explicit allowed transitions. CANCELLED and REFUNDED are terminal.
     * REQUESTED may jump straight to CONFIRMED/CANCELLED because the current
     * choreography does not persist the intermediate saga states.
     * <p>
     * Fase 5: CONFIRMED is no longer terminal — it advances through the fulfillment
     * chain (SHIPPED → DELIVERED) via the manual status endpoint, and any post-confirmation
     * state can be REFUNDED.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case REQUESTED -> target == INVENTORY_RESERVED
                    || target == INVENTORY_INSUFFICIENT
                    || target == PENDING_PAYMENT
                    || target == CONFIRMED
                    || target == CANCELLED
                    || target == TIMEOUT;
            case INVENTORY_RESERVED -> target == PAYMENT_AUTHORIZED
                    || target == PAYMENT_FAILED
                    || target == PENDING_PAYMENT
                    || target == CONFIRMED
                    || target == CANCELLED
                    || target == TIMEOUT;
            case PAYMENT_AUTHORIZED -> target == CONFIRMED
                    || target == CANCELLED
                    || target == TIMEOUT;
            case PENDING_PAYMENT -> target == PAYMENT_AUTHORIZED
                    || target == PAYMENT_FAILED
                    || target == CONFIRMED
                    || target == CANCELLED
                    || target == TIMEOUT;
            case PAYMENT_FAILED, INVENTORY_INSUFFICIENT, TIMEOUT -> target == CANCELLED;
            case CONFIRMED -> target == SHIPPED || target == REFUNDED;
            case SHIPPED -> target == DELIVERED || target == REFUNDED;
            case DELIVERED -> target == REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
    }
}
