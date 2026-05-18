package code.with.vanilson.productservice.kafka;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * InventoryCompensationConsumer — Saga Compensation Step
 * <p>
 * Consumes payment.failed events and releases previously reserved stock.
 * Uses InventoryReservation records to know exactly what to release.
 * Idempotent: checks reservation status before releasing (RELEASED = skip).
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCompensationConsumer {

    private final InventoryReservationRepository reservationRepository;
    private final ProductRepository              productRepository;
    private final KafkaTemplate<String, Object>  kafkaTemplate;

    private static final String TOPIC_RELEASED = "inventory.released";

    @KafkaListener(
            topics = "payment.failed",
            groupId = "inventory-compensation-group",
            containerFactory = "compensationKafkaListenerContainerFactory")
    @Transactional
    public void onPaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[InventoryCompensation] payment.failed received: correlationId=[{}] reason=[{}] partition=[{}] offset=[{}]",
                event.correlationId(), event.reason(), partition, offset);

        List<InventoryReservation> reservations = reservationRepository
                .findByCorrelationIdAndStatus(event.correlationId(), InventoryReservation.ReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            log.info("[InventoryCompensation] No RESERVED records for correlationId=[{}] — already released or never reserved",
                    event.correlationId());
            ack.acknowledge();
            return;
        }

        for (InventoryReservation reservation : reservations) {
            productRepository.findById(reservation.getProductId()).ifPresent(product -> {
                double restored = product.getAvailableQuantity() + reservation.getReservedQuantity();
                product.setAvailableQuantity(restored);
                productRepository.save(product);
                log.info("[InventoryCompensation] Stock restored: productId=[{}] qty=[+{}] newTotal=[{}] correlationId=[{}]",
                        product.getId(), reservation.getReservedQuantity(), restored, event.correlationId());
            });

            reservation.setStatus(InventoryReservation.ReservationStatus.RELEASED);
            reservation.setReleasedAt(LocalDateTime.now());
            reservationRepository.save(reservation);
        }

        publishInventoryReleased(event);

        log.info("[InventoryCompensation] Released {} reservations for correlationId=[{}]",
                reservations.size(), event.correlationId());
        ack.acknowledge();
    }

    private void publishInventoryReleased(PaymentFailedEvent event) {
        InventoryReleasedEvent released = new InventoryReleasedEvent(
                UUID.randomUUID().toString(),
                event.correlationId(),
                event.orderReference(),
                event.reason(),
                Instant.now(),
                1
        );
        kafkaTemplate.send(TOPIC_RELEASED, event.correlationId(), released);
    }
}
