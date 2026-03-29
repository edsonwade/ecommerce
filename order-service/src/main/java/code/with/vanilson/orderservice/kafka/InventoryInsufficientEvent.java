package code.with.vanilson.orderservice.kafka;

import java.time.Instant;

/**
 * InventoryInsufficientEvent — Kafka Event (Phase 3)
 * <p>
 * Consumed by order-service from topic: inventory.insufficient
 * Published by product-service when stock is not available.
 * Triggers CANCELLED state (no payment attempted).
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
public record InventoryInsufficientEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        Integer productId,
        double  requestedQty,
        double  availableQty,
        Instant occurredAt,
        int     schemaVersion
) {}
