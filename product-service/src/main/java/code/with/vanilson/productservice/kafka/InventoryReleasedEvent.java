package code.with.vanilson.productservice.kafka;

import java.time.Instant;

/**
 * InventoryReleasedEvent — Kafka event published by product-service
 * after releasing reserved stock when a payment fails.
 * Topic: inventory.released
 * Consumed by: order-service (confirms CANCELLED state)
 *
 * @author vamuhong
 * @version 3.0
 */
public record InventoryReleasedEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        String  reason,
        Instant occurredAt,
        int     schemaVersion
) {}
