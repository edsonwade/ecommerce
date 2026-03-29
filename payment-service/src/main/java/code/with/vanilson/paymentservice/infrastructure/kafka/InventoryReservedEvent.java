package code.with.vanilson.paymentservice.infrastructure.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * InventoryReservedEvent — Local Kafka event DTO (payment-service)
 * <p>
 * Consumed from topic: inventory.reserved
 * Owned by payment-service — no shared JAR with product-service.
 * Field names match product-service's InventoryReservedEvent serialisation.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
public record InventoryReservedEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        String  customerId,
        String  customerEmail,
        String  customerFirstname,
        String  customerLastname,
        List<ReservedItem> reservedItems,
        BigDecimal totalAmount,
        String  paymentMethod,
        Instant occurredAt,
        int     schemaVersion
) {
    public record ReservedItem(Integer productId, String productName,
                               double quantity, BigDecimal unitPrice) {}
}
