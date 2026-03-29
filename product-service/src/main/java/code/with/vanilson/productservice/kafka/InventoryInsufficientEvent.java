package code.with.vanilson.productservice.kafka;

import java.time.Instant;

/**
 * InventoryInsufficientEvent — Kafka event published by product-service
 * when stock cannot satisfy the order request.
 * Topic: inventory.insufficient
 * Consumed by: order-service (cancels the order — no payment attempted)
 *
 * @author vamuhong
 * @version 3.0
 */
public record InventoryInsufficientEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        Integer productId,
        String  productName,
        double  requestedQty,
        double  availableQty,
        Instant occurredAt,
        int     schemaVersion
) {}
