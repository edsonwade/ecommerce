package code.with.vanilson.productservice.kafka;

import java.time.Instant;
import java.util.List;

/**
 * InventoryReservedEvent — Kafka event published by product-service
 * after successfully reserving stock for all items in an order.
 * Topic: inventory.reserved
 * Consumed by: payment-service (saga step 2)
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
        java.math.BigDecimal totalAmount,
        String  paymentMethod,
        Instant occurredAt,
        int     schemaVersion
) {
    public record ReservedItem(Integer productId, String productName, double quantity,
                               java.math.BigDecimal unitPrice) {}
}
