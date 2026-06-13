package code.with.vanilson.productservice.kafka;

import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import code.with.vanilson.productservice.InventoryReservationService;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.MDC;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * InventoryReservationConsumer — Infrastructure Layer (Saga Step 1)
 * <p>
 * Consumes order.requested events and atomically reserves stock.
 * <p>
 * Saga choreography — product-service's role:
 * 1. Consume order.requested
 * 2. Attempt to reserve stock for all products (pessimistic lock)
 * 3a. SUCCESS → publish inventory.reserved → payment-service processes payment
 * 3b. FAILURE → publish inventory.insufficient → order-service cancels order
 * <p>
 * Idempotency: if the same eventId arrives twice (Kafka at-least-once),
 * the second reservation attempt will fail at the DB lock and the same
 * outcome event is published again — idempotent from order-service's perspective.
 * <p>
 * Manual acknowledgement: offset committed only after Kafka publish succeeds.
 * If publish fails → Kafka retries → product-service processes again (idempotent).
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationConsumer {

    private final InventoryReservationService        inventoryReservationService;
    private final InventoryReservationRepository     reservationRepository;
    private final KafkaTemplate<String, Object>      kafkaTemplate;
    private final MeterRegistry                      meterRegistry;
    private final TransactionTemplate                transactionTemplate;

    private static final String TOPIC_RESERVED     = "inventory.reserved";
    private static final String TOPIC_INSUFFICIENT = "inventory.insufficient";

    @KafkaListener(
            topics = "order.requested",
            groupId = "inventory-reservation-group",
            containerFactory = "inventoryKafkaListenerContainerFactory")
    public void onOrderRequested(
            @Payload OrderRequestedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            MDC.put("correlationId", event.correlationId());
            MDC.put("sagaStep", "inventory-reservation");

            log.info("[InventoryConsumer] order.requested received: correlationId=[{}] products=[{}] partition=[{}] offset=[{}]",
                    event.correlationId(), event.products().size(), partition, offset);

            try {
                // Programmatic TX instead of @Transactional on the listener:
                // ProductPurchaseException crosses the MANDATORY-propagation
                // service proxy and marks the TX rollback-only; catching it
                // inside a declarative TX would make the commit at the listener
                // boundary throw UnexpectedRollbackException (retries + DLQ).
                // Here the TX ends (rolled back) before the catch below runs.
                List<InventoryReservedEvent.ReservedItem> reservedItems =
                        transactionTemplate.execute(status ->
                                reserveStock(event.products(), event.correlationId()));

                publishInventoryReserved(event, reservedItems);
                log.info("[InventoryConsumer] Stock reserved. correlationId=[{}] items=[{}]",
                        event.correlationId(), reservedItems.size());

                meterRegistry.counter("saga.step.completed", "step", "inventory", "outcome", "success").increment();

            } catch (ProductPurchaseException ex) {
                // Stock insufficient — extract failing product details from the exception
                log.warn("[InventoryConsumer] Insufficient stock for correlationId=[{}]: {}",
                        event.correlationId(), ex.getMessage());
                publishInventoryInsufficient(event, ex);
                meterRegistry.counter("saga.step.completed", "step", "inventory", "outcome", "failure").increment();
            }

            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }

    // -------------------------------------------------------

    /**
     * Reserves stock via the shared InventoryReservationService (pessimistic
     * locking, all-or-nothing within this consumer's transaction), then records
     * the saga-specific InventoryReservation rows used by the compensation flow.
     */
    private List<InventoryReservedEvent.ReservedItem> reserveStock(
            List<OrderRequestedEvent.ProductPurchaseItem> items, String correlationId) {

        List<InventoryReservationService.ReservedLine> lines =
                inventoryReservationService.reserveStock(items.stream()
                        .map(i -> new InventoryReservationService.ReservationItem(i.productId(), i.quantity()))
                        .toList());

        List<InventoryReservedEvent.ReservedItem> reserved = new ArrayList<>();

        for (InventoryReservationService.ReservedLine line : lines) {
            Product product = line.product();

            reservationRepository.save(InventoryReservation.builder()
                    .correlationId(correlationId)
                    .productId(product.getId())
                    .reservedQuantity((int) line.quantity())
                    .status(InventoryReservation.ReservationStatus.RESERVED)
                    .createdAt(java.time.LocalDateTime.now())
                    .build());

            log.debug("[InventoryConsumer] Stock reserved: productId=[{}] qty=[{}] newAvailable=[{}] correlationId=[{}]",
                    product.getId(), line.quantity(), product.getAvailableQuantity(), correlationId);

            reserved.add(new InventoryReservedEvent.ReservedItem(
                    product.getId(), product.getName(),
                    line.quantity(), product.getPrice()));
        }

        return reserved;
    }

    private void publishInventoryReserved(OrderRequestedEvent order,
                                           List<InventoryReservedEvent.ReservedItem> reserved) {
        InventoryReservedEvent event = new InventoryReservedEvent(
                UUID.randomUUID().toString(),
                order.correlationId(),
                order.orderReference(),
                order.customerId(),
                order.customerEmail(),
                order.customerFirstname(),
                order.customerLastname(),
                reserved,
                order.totalAmount(),
                order.paymentMethod(),
                // pass tenantId + orderId through so payment-service can persist
                order.tenantId(),
                order.orderId(),
                Instant.now(),
                1
        );
        kafkaTemplate.send(TOPIC_RESERVED, order.correlationId(), event);
    }

    private void publishInventoryInsufficient(OrderRequestedEvent order,
                                               ProductPurchaseException cause) {
        InventoryInsufficientEvent event = new InventoryInsufficientEvent(
                UUID.randomUUID().toString(),
                order.correlationId(),
                order.orderReference(),
                cause.getProductId(),
                cause.getProductName(),
                cause.getRequestedQty(),
                cause.getAvailableQty(),
                Instant.now(),
                1
        );
        kafkaTemplate.send(TOPIC_INSUFFICIENT, order.correlationId(), event);
    }
}
