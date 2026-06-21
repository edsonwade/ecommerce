package code.with.vanilson.customerservice.kafka;

import code.with.vanilson.customerservice.Address;
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
        Address address = customer.getAddress();
        CustomerProfileEvent event = new CustomerProfileEvent(
                UUID.randomUUID().toString(),
                customer.getCustomerId(),
                customer.getFirstname(),
                customer.getLastname(),
                customer.getEmail(),
                address != null ? address.getStreet() : null,
                address != null ? address.getHouseNumber() : null,
                address != null ? address.getZipCode() : null,
                address != null ? address.getCity() : null,
                address != null ? address.getCountry() : null,
                eventType,
                Instant.now(),
                2
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
