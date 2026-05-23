package code.with.vanilson.customerservice.kafka;

import code.with.vanilson.customerservice.Customer;
import code.with.vanilson.customerservice.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * UserRegisteredConsumer — creates a customer profile from a {@code user.registered} event.
 * <p>
 * Replaces the synchronous Feign call that previously existed in
 * {@code authentication-service → customer-service} on registration.
 * <p>
 * Idempotency: if a customer with {@code userId} already exists (login backfill or duplicate
 * event), the consumer acknowledges and skips creation. This makes the handler safe under
 * at-least-once delivery.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final CustomerRepository customerRepository;

    @KafkaListener(
            topics = "user.registered",
            groupId = "customer-user-registration-group",
            containerFactory = "customerKafkaListenerContainerFactory"
    )
    public void onUserRegistered(@Payload UserRegisteredEvent event, Acknowledgment ack) {
        log.info("[UserRegisteredConsumer] Received user.registered for userId=[{}] email=[{}]",
                event.userId(), event.email());

        // Idempotency guard: skip if customer already exists (duplicate event or login backfill)
        if (customerRepository.existsById(event.userId())) {
            log.info("[UserRegisteredConsumer] Customer already exists for userId=[{}], skipping",
                    event.userId());
            ack.acknowledge();
            return;
        }

        Customer customer = Customer.builder()
                .customerId(event.userId())
                .firstname(event.firstname())
                .lastname(event.lastname())
                .email(event.email())
                .build();

        customerRepository.save(customer);
        ack.acknowledge();

        log.info("[UserRegisteredConsumer] Customer profile created for userId=[{}] email=[{}]",
                event.userId(), event.email());
    }
}
