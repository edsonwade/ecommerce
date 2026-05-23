package code.with.vanilson.orderservice.event;

import java.time.Instant;

/**
 * OrderStatusChangedEvent — Spring Application Event (Phase 3)
 * <p>
 * Published internally (in-process) whenever the saga updates an order status.
 * Consumed by OrderStatusSseController to push real-time updates to connected clients.
 * <p>
 * Not a Kafka event — this is an intra-JVM Spring event, so no serialization overhead.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
public record OrderStatusChangedEvent(
        String correlationId,
        String status,
        String orderReference,
        Instant occurredAt
) {}
