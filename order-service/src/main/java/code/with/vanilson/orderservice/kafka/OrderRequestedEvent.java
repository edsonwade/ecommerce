package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.orderservice.product.ProductPurchaseRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * OrderRequestedEvent — Kafka Event (Phase 3)
 * <p>
 * Published by OrderService when a new order is created.
 * Topic: order.requested
 * Partition key: correlationId (all saga events for the same order go to the same partition)
 * <p>
 * This event triggers the Choreography Saga:
 * 1. product-service consumes → reserves inventory → publishes inventory.reserved
 * 2. payment-service consumes inventory.reserved → processes payment → publishes payment.authorized
 * 3. order-service consumes payment.authorized → confirms order
 * <p>
 * schemaVersion: enables forward compatibility — consumers check version
 * and handle unknown fields gracefully (Open/Closed principle for events).
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
public record OrderRequestedEvent(
        String                       eventId,         // UUID — idempotency check by consumers
        String                       correlationId,   // client-facing tracking ID
        String                       customerId,
        String                       customerEmail,
        String                       customerFirstname,
        String                       customerLastname,
        List<ProductPurchaseRequest> products,
        BigDecimal                   totalAmount,
        PaymentMethod                paymentMethod,
        String                       orderReference,
        Instant                      occurredAt,
        int                          schemaVersion    // always 1 for now
) {}
