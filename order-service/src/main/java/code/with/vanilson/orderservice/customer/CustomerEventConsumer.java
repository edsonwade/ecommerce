package code.with.vanilson.orderservice.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CustomerEventConsumer — maintains the {@link CustomerSnapshot} CQRS read model.
 * <p>
 * Listens to {@code customer.profile} topic and upserts a local snapshot row for each
 * customer create/update event from customer-service.
 * <p>
 * Manual acknowledgment: offset is committed only after the DB write succeeds.
 * This guarantees at-least-once delivery — idempotent upsert handles duplicates safely.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerEventConsumer {

    private final CustomerSnapshotRepository snapshotRepository;

    @KafkaListener(
            topics = "customer.profile",
            groupId = "order-customer-snapshot-group",
            containerFactory = "sagaKafkaListenerContainerFactory"
    )
    @Transactional
    public void onCustomerProfile(@Payload CustomerProfileEvent event, Acknowledgment ack) {
        log.info("[CustomerEventConsumer] Received {} event for customerId=[{}] eventId=[{}]",
                event.eventType(), event.customerId(), event.eventId());

        CustomerSnapshot snapshot = snapshotRepository.findById(event.customerId())
                .orElse(new CustomerSnapshot());

        snapshot.setCustomerId(event.customerId());
        snapshot.setFirstname(event.firstname());
        snapshot.setLastname(event.lastname());
        snapshot.setEmail(event.email());
        // Shipping address (schemaVersion >= 2). Older v1 events carry null here; we only
        // overwrite when the event actually supplies a value so a v1 replay can't wipe an
        // address a later v2 event already populated.
        if (event.street() != null)      snapshot.setStreet(event.street());
        if (event.houseNumber() != null) snapshot.setHouseNumber(event.houseNumber());
        if (event.zipCode() != null)     snapshot.setZipCode(event.zipCode());
        if (event.city() != null)        snapshot.setCity(event.city());
        if (event.country() != null)     snapshot.setCountry(event.country());
        snapshot.setLastUpdated(LocalDateTime.now());

        snapshotRepository.save(snapshot);
        ack.acknowledge();

        log.info("[CustomerEventConsumer] Snapshot upserted for customerId=[{}]", event.customerId());
    }
}
