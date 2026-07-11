package code.with.vanilson.productservice.kafka;

import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import code.with.vanilson.productservice.InventoryReservationService;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * Idempotency (B4): the InventoryReservation rows written in the SAME transaction
 * as the stock deduction double as the processed-event record. On every delivery
 * the consumer first checks for existing rows for the event's correlationId:
 * - rows with status RESERVED → a previous delivery already deducted stock but the
 *   publish/ack may have been lost → re-publish inventory.reserved rebuilt from the
 *   persisted rows (at-least-once), WITHOUT touching stock again;
 * - rows all RELEASED → the saga was already compensated (payment.failed) → ack and
 *   skip; re-publishing inventory.reserved here would re-trigger payment for a dead
 *   order;
 * - no rows → first effective delivery → reserve normally. A redelivered event that
 *   previously failed with insufficient stock leaves no rows, so it reprocesses and
 *   republishes the same inventory.insufficient outcome — idempotent by outcome.
 * <p>
 * Manual acknowledgement: offset committed only after Kafka publish succeeds.
 * If publish fails → Kafka retries → the guard above makes the retry safe.
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
    private final ProductRepository                  productRepository;
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

            // Idempotency guard (B4): reservation rows are committed in the same TX as
            // the stock deduction, so any row for this correlationId means a previous
            // delivery already deducted stock — deducting again would double-reserve.
            List<InventoryReservation> priorReservations =
                    reservationRepository.findByCorrelationId(event.correlationId());
            if (!priorReservations.isEmpty()) {
                handleDuplicateDelivery(event, priorReservations);
                ack.acknowledge();
                return;
            }

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
     * Handles a redelivered order.requested whose reservation already committed.
     * <p>
     * If the rows are still RESERVED the previous delivery deducted stock but its
     * publish/ack may have been lost — re-publish inventory.reserved rebuilt from
     * the persisted rows so the saga can progress (at-least-once; payment-service
     * deduplicates by orderReference). If every row is already RELEASED the saga
     * was compensated after a payment failure — publishing inventory.reserved now
     * would re-trigger payment for a dead order, so the duplicate is only logged.
     */
    private void handleDuplicateDelivery(OrderRequestedEvent event,
                                         List<InventoryReservation> priorReservations) {
        List<InventoryReservation> stillReserved = priorReservations.stream()
                .filter(r -> r.getStatus() == InventoryReservation.ReservationStatus.RESERVED)
                .toList();

        if (stillReserved.isEmpty()) {
            log.info("[InventoryConsumer] Duplicate order.requested ignored — reservations already RELEASED (saga compensated). correlationId=[{}]",
                    event.correlationId());
            meterRegistry.counter("saga.step.duplicate", "step", "inventory", "outcome", "released").increment();
            return;
        }

        publishInventoryReserved(event, rebuildReservedItems(stillReserved));
        log.info("[InventoryConsumer] Duplicate order.requested — stock already reserved, re-published inventory.reserved without deducting again. correlationId=[{}]",
                event.correlationId());
        meterRegistry.counter("saga.step.duplicate", "step", "inventory", "outcome", "reserved").increment();
    }

    /**
     * Rebuilds the reserved-items payload from persisted reservation rows.
     * Product name/price come from the current catalog row (same source the
     * original publish used); quantity comes from the reservation record.
     */
    private List<InventoryReservedEvent.ReservedItem> rebuildReservedItems(
            List<InventoryReservation> reservations) {
        Map<Integer, Product> productsById = productRepository.findAllById(
                        reservations.stream().map(InventoryReservation::getProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<InventoryReservedEvent.ReservedItem> items = new ArrayList<>();
        for (InventoryReservation reservation : reservations) {
            Product product = productsById.get(reservation.getProductId());
            if (product == null) {
                log.warn("[InventoryConsumer] Reserved product no longer in catalog — omitted from re-published event: productId=[{}] correlationId=[{}]",
                        reservation.getProductId(), reservation.getCorrelationId());
                continue;
            }
            items.add(new InventoryReservedEvent.ReservedItem(
                    product.getId(), product.getName(),
                    reservation.getReservedQuantity(), product.getPrice()));
        }
        return items;
    }

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
