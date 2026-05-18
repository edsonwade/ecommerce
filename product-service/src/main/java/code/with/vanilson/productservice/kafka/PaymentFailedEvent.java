package code.with.vanilson.productservice.kafka;

import java.time.Instant;

/**
 * PaymentFailedEvent — local DTO consumed from topic: payment.failed
 * Used by InventoryCompensationConsumer to release reserved stock.
 *
 * @author vamuhong
 * @version 4.0
 */
public record PaymentFailedEvent(
        String  eventId,
        String  correlationId,
        String  orderReference,
        String  reason,
        Instant occurredAt,
        int     schemaVersion
) {}
