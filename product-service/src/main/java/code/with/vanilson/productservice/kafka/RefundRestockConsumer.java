package code.with.vanilson.productservice.kafka;

import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import code.with.vanilson.productservice.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RefundRestockConsumer — Fase 6 (basic refunds), clone of
 * {@link InventoryCompensationConsumer}'s semantics.
 * <p>
 * Consumes {@code order.refunded} (order-service's outbox, after a payment refund is
 * applied) and restores the quantities that were reserved for that order —
 * RESERVED→RELEASED, idempotent via the same reservation-status check the compensation
 * consumer uses (redelivery of the same event is a no-op once every row is RELEASED).
 * Terminal step — unlike the compensation path, restock does not publish a follow-up event.
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundRestockConsumer {

    private final InventoryReservationRepository reservationRepository;
    private final ProductRepository              productRepository;
    private final MeterRegistry                  meterRegistry;

    @KafkaListener(
            topics = "order.refunded",
            groupId = "refund-restock-group",
            containerFactory = "inventoryKafkaListenerContainerFactory")
    @Transactional
    public void onOrderRefunded(
            @Payload OrderRefundedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            MDC.put("correlationId", event.correlationId());
            MDC.put("sagaStep", "refund-restock");

            log.info("[RefundRestock] order.refunded received: correlationId=[{}] partition=[{}] offset=[{}]",
                    event.correlationId(), partition, offset);

            List<InventoryReservation> reservations = reservationRepository
                    .findByCorrelationIdAndStatus(event.correlationId(), InventoryReservation.ReservationStatus.RESERVED);

            if (reservations.isEmpty()) {
                log.info("[RefundRestock] No RESERVED records for correlationId=[{}] — already released or never reserved",
                        event.correlationId());
                ack.acknowledge();
                return;
            }

            for (InventoryReservation reservation : reservations) {
                productRepository.findById(reservation.getProductId()).ifPresent(product -> {
                    double restored = product.getAvailableQuantity() + reservation.getReservedQuantity();
                    product.setAvailableQuantity(restored);
                    productRepository.save(product);
                    log.info("[RefundRestock] Stock restored: productId=[{}] qty=[+{}] newTotal=[{}] correlationId=[{}]",
                            product.getId(), reservation.getReservedQuantity(), restored, event.correlationId());
                });

                reservation.setStatus(InventoryReservation.ReservationStatus.RELEASED);
                reservation.setReleasedAt(LocalDateTime.now());
                reservationRepository.save(reservation);
            }

            log.info("[RefundRestock] Restocked {} reservations for correlationId=[{}]",
                    reservations.size(), event.correlationId());

            meterRegistry.counter("saga.step.completed", "step", "refund-restock", "outcome", "success").increment();

            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }
}
