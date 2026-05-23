package code.with.vanilson.customerservice.kafka;

import java.time.Instant;

/**
 * CustomerProfileEvent — published to {@code customer.profile} topic
 * whenever a customer profile is created or updated in customer-service.
 * <p>
 * Consumed by order-service to maintain a local CustomerSnapshot (CQRS read model).
 * schemaVersion allows consumers to handle multiple event shapes during rolling deploys.
 * </p>
 *
 * @param eventId       globally unique event identifier (UUID)
 * @param customerId    matches customer-service MongoDB {@code _id}
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
