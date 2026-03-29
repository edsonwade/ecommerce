package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * OrderSagaConsumer — Infrastructure Layer (Kafka Consumer)
 * <p>
 * Listens for saga outcome events and updates the order status accordingly.
 * <p>
 * Choreography Saga — this consumer handles:
 * - payment.authorized   → CONFIRMED (happy path)
 * - payment.failed       → CANCELLED (payment declined)
 * - inventory.insufficient → CANCELLED (out of stock, no payment attempted)
 * <p>
 * Idempotency: each event carries an eventId. If the same event arrives twice
 * (at-least-once delivery), the status update is idempotent — setting an already
 * CONFIRMED order to CONFIRMED again has no effect.
 * <p>
 * Manual acknowledgement: offset committed only after DB update succeeds.
 * If the DB write fails → Kafka retries the event → idempotent handler safe.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaConsumer {

    private final OrderService  orderService;
    private final MessageSource messageSource;

    // -------------------------------------------------------
    // HAPPY PATH — payment authorised → CONFIRMED
    // -------------------------------------------------------

    @KafkaListener(
            topics = "payment.authorized",
            groupId = "order-saga-group",
            containerFactory = "sagaKafkaListenerContainerFactory")
    public void onPaymentAuthorized(
            @Payload PaymentAuthorizedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[OrderSagaConsumer] payment.authorized received: correlationId=[{}] partition=[{}] offset=[{}]",
                event.correlationId(), partition, offset);

        orderService.updateStatus(event.correlationId(), OrderStatus.CONFIRMED);

        log.info("[OrderSagaConsumer] Order CONFIRMED: correlationId=[{}]", event.correlationId());
        ack.acknowledge();
    }

    // -------------------------------------------------------
    // COMPENSATION — payment failed → CANCELLED
    // -------------------------------------------------------

    @KafkaListener(
            topics = "payment.failed",
            groupId = "order-saga-group",
            containerFactory = "sagaKafkaListenerContainerFactory")
    public void onPaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.warn("[OrderSagaConsumer] payment.failed received: correlationId=[{}] reason=[{}] partition=[{}] offset=[{}]",
                event.correlationId(), event.reason(), partition, offset);

        orderService.updateStatus(event.correlationId(), OrderStatus.CANCELLED);

        log.warn("[OrderSagaConsumer] Order CANCELLED (payment failed): correlationId=[{}]", event.correlationId());
        ack.acknowledge();
    }

    // -------------------------------------------------------
    // COMPENSATION — inventory insufficient → CANCELLED (no payment attempted)
    // -------------------------------------------------------

    @KafkaListener(
            topics = "inventory.insufficient",
            groupId = "order-saga-group",
            containerFactory = "sagaKafkaListenerContainerFactory")
    public void onInventoryInsufficient(
            @Payload InventoryInsufficientEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.warn("[OrderSagaConsumer] inventory.insufficient received: correlationId=[{}] productId=[{}] " +
                 "requested=[{}] available=[{}] partition=[{}] offset=[{}]",
                event.correlationId(), event.productId(),
                event.requestedQty(), event.availableQty(), partition, offset);

        orderService.updateStatus(event.correlationId(), OrderStatus.INVENTORY_INSUFFICIENT);
        orderService.updateStatus(event.correlationId(), OrderStatus.CANCELLED);

        log.warn("[OrderSagaConsumer] Order CANCELLED (insufficient stock): correlationId=[{}]",
                event.correlationId());
        ack.acknowledge();
    }
}
