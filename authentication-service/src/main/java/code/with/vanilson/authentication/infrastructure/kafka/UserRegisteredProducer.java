package code.with.vanilson.authentication.infrastructure.kafka;

import code.with.vanilson.authentication.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * UserRegisteredProducer — publishes a {@link UserRegisteredEvent} to {@code user.registered}
 * topic after a new user is persisted by {@code AuthService.register()}.
 * <p>
 * Partition key: userId — ensures ordering per user in the consumer.
 * customer-service consumes this event to create the customer profile asynchronously,
 * eliminating the synchronous Feign call from the registration path.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredProducer {

    public static final String TOPIC = "user.registered";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a {@link UserRegisteredEvent} for the given user.
     *
     * @param user the newly persisted user entity
     */
    public void publishUserRegistered(User user) {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID().toString(),
                String.valueOf(user.getId()),
                user.getFirstname(),
                user.getLastname(),
                user.getEmail(),
                user.getTenantId(),
                Instant.now(),
                1
        );

        kafkaTemplate.send(TOPIC, String.valueOf(user.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[UserRegisteredProducer] Failed to publish user.registered for userId=[{}]: {}",
                                user.getId(), ex.getMessage(), ex);
                    } else {
                        log.info("[UserRegisteredProducer] Published user.registered for userId=[{}] partition=[{}] offset=[{}]",
                                user.getId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
