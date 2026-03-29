package code.with.vanilson.productservice.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * OrderRequestedEvent — Local Kafka event DTO (product-service)
 * <p>
 * Consumed from topic: order.requested
 * Owned by product-service — no shared JAR with order-service.
 * Field names must match what order-service serialises.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
public record OrderRequestedEvent(
        String                       eventId,
        String                       correlationId,
        String                       customerId,
        String                       customerEmail,
        String                       customerFirstname,
        String                       customerLastname,
        List<ProductPurchaseItem>    products,
        BigDecimal                   totalAmount,
        String                       paymentMethod,
        String                       orderReference,
        Instant                      occurredAt,
        int                          schemaVersion
) {
    /** Nested DTO matching order-service's ProductPurchaseRequest structure. */
    public record ProductPurchaseItem(Integer productId, double quantity) {}
}
