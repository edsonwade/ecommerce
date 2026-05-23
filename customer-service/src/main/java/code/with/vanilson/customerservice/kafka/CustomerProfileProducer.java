package code.with.vanilson.customerservice.kafka;

import code.with.vanilson.customerservice.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * CustomerProfileProducer — publishes {@link CustomerProfileEvent} to {@code customer.profile} topic.
 * <p>
 * Partition key: customerId — ensures all events for the same customer land on the same partition,
 * preserving ordering for order-service's snapshot consumer.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerProfileProducer {

    public static final String TOPIC = "customer.profile";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a profile event for the given customer.
     *
     * @param customer  the saved customer entity
     * @param eventType {@code "CREATED"} or {@code "UPDATED"}
     */
    public void publishProfileEvent(Customer customer, String eventType) {
        CustomerProfileEvent event = new CustomerProfileEvent(
                UUID.randomUUID().toString(),
                customer.getCustomerId(),
                customer.getFirstname(),
                customer.getLastname(),
                customer.getEmail(),
                eventType,
                Instant.now(),
                1
        );

        kafkaTemplate.send(TOPIC, customer.getCustomerId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[CustomerProfileProducer] Failed to publish {} event for customerId=[{}]: {}",
                                eventType, customer.getCustomerId(), ex.getMessage(), ex);
                    } else {
                        log.info("[CustomerProfileProducer] Published {} event for customerId=[{}] partition=[{}] offset=[{}]",
                                eventType, customer.getCustomerId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
