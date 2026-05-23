package code.with.vanilson.authentication.infrastructure.kafka;

import java.time.Instant;

/**
 * UserRegisteredEvent — published to {@code user.registered} topic when a new user registers
 * via authentication-service.
 * <p>
 * Consumed by customer-service to create the customer profile asynchronously,
 * replacing the previous synchronous Feign call in {@code AuthService.register()}.
 * <p>
 * Login backfill ({@code AuthService.login()}) remains as a safety net during the
 * eventual-consistency window.
 * </p>
 *
 * @param eventId       globally unique event identifier (UUID)
 * @param userId        the newly created user's DB id (Long as String)
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
