package code.with.vanilson.customerservice.kafka;

import java.time.Instant;

/**
 * UserRegisteredEvent — local DTO for customer-service.
 * <p>
 * Mirrors the event published by authentication-service to {@code user.registered} topic.
 * customer-service MUST NOT import classes from authentication-service JAR —
 * contract is Kafka JSON only.
 * </p>
 *
 * @param eventId       globally unique event identifier (UUID)
 * @param userId        the new user's DB id (maps to customer customerId)
 * @param firstname     user first name
 * @param lastname      user last name
 * @param email         user email
 * @param tenantId      tenant the user belongs to
 * @param occurredAt    wall-clock time of registration
 * @param schemaVersion schema version for forward/backward compatibility
 */
public record UserRegisteredEvent(
        String eventId,
        String userId,
        String firstname,
        String lastname,
        String email,
        String tenantId,
        Instant occurredAt,
        int schemaVersion
) {}
