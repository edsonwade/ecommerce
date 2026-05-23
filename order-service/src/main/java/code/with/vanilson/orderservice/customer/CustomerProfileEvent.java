package code.with.vanilson.orderservice.customer;

import java.time.Instant;

/**
 * CustomerProfileEvent — local DTO for order-service.
 * <p>
 * Mirrors the event published by customer-service to {@code customer.profile} topic.
 * order-service MUST NOT import classes from customer-service JAR — contract is Kafka JSON only.
 * </p>
 *
 * @param eventId       globally unique event identifier (UUID)
 * @param customerId    customer ID (matches customer-service MongoDB {@code _id})
 * @param firstname     customer first name
 * @param lastname      customer last name
 * @param email         customer email
 * @param eventType     {@code CREATED} or {@code UPDATED}
 * @param occurredAt    wall-clock time of the mutation
 * @param schemaVersion schema version for forward/backward compatibility
 */
public record CustomerProfileEvent(
        String eventId,
        String customerId,
        String firstname,
        String lastname,
        String email,
        String eventType,
        Instant occurredAt,
        int schemaVersion
) {}
