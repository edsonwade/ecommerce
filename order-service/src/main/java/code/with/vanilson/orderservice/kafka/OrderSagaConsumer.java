package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.customer.CustomerInfo;
import code.with.vanilson.orderservice.event.OrderStatusChangedEvent;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.orderservice.product.ProductPurchaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.List;

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

    private final OrderService             orderService;
    private final OrderProducer            orderProducer;
    private final MessageSource            messageSource;
    private final MeterRegistry            meterRegistry;
    private final ApplicationEventPublisher eventPublisher;

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

        try {
            MDC.put("correlationId", event.correlationId());
            MDC.put("sagaStep", "order-confirmation");

            log.info("[OrderSagaConsumer] payment.authorized received: correlationId=[{}] partition=[{}] offset=[{}]",
                    event.correlationId(), partition, offset);

            orderService.updateStatus(event.correlationId(), OrderStatus.CONFIRMED);
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    event.correlationId(), OrderStatus.CONFIRMED.name(),
                    event.orderReference(), Instant.now()));

            sendOrderConfirmationNotification(event);

            log.info("[OrderSagaConsumer] Order CONFIRMED: correlationId=[{}]", event.correlationId());
            meterRegistry.counter("saga.step.completed", "step", "order", "outcome", "success").increment();
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
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

        try {
            MDC.put("correlationId", event.correlationId());
            MDC.put("sagaStep", "order-cancellation");

            log.warn("[OrderSagaConsumer] payment.failed received: correlationId=[{}] reason=[{}] partition=[{}] offset=[{}]",
                    event.correlationId(), event.reason(), partition, offset);

            orderService.updateStatus(event.correlationId(), OrderStatus.CANCELLED);
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    event.correlationId(), OrderStatus.CANCELLED.name(),
                    event.orderReference(), Instant.now()));

            log.warn("[OrderSagaConsumer] Order CANCELLED (payment failed): correlationId=[{}]", event.correlationId());
            meterRegistry.counter("saga.step.completed", "step", "order", "outcome", "failure").increment();
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
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

        try {
            MDC.put("correlationId", event.correlationId());
            MDC.put("sagaStep", "order-cancellation");

            log.warn("[OrderSagaConsumer] inventory.insufficient received: correlationId=[{}] productId=[{}] " +
                     "requested=[{}] available=[{}] partition=[{}] offset=[{}]",
                    event.correlationId(), event.productId(),
                    event.requestedQty(), event.availableQty(), partition, offset);

            orderService.updateStatus(event.correlationId(), OrderStatus.INVENTORY_INSUFFICIENT);
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    event.correlationId(), OrderStatus.INVENTORY_INSUFFICIENT.name(),
                    event.orderReference(), Instant.now()));

            orderService.updateStatus(event.correlationId(), OrderStatus.CANCELLED);
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    event.correlationId(), OrderStatus.CANCELLED.name(),
                    event.orderReference(), Instant.now()));

            log.warn("[OrderSagaConsumer] Order CANCELLED (insufficient stock): correlationId=[{}]",
                    event.correlationId());
            meterRegistry.counter("saga.step.completed", "step", "order", "outcome", "failure").increment();
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }

    // -------------------------------------------------------
    // NOTIFICATION — builds OrderConfirmation from enriched event
    // -------------------------------------------------------

    private void sendOrderConfirmationNotification(PaymentAuthorizedEvent event) {
        try {
            CustomerInfo customer = new CustomerInfo(
                    event.customerId(), event.customerFirstname(),
                    event.customerLastname(), event.customerEmail(), null);

            List<ProductPurchaseResponse> products = event.reservedItems() != null
                    ? event.reservedItems().stream()
                    .map(ri -> new ProductPurchaseResponse(
                            ri.productId(), ri.productName(), null,
                            ri.unitPrice(), ri.quantity()))
                    .toList()
                    : List.of();

            PaymentMethod method;
            try {
                method = PaymentMethod.valueOf(event.paymentMethod().toUpperCase());
            } catch (Exception ex) {
                method = PaymentMethod.CREDIT_CARD;
            }

            OrderConfirmation confirmation = new OrderConfirmation(
                    event.orderReference(), event.totalAmount(),
                    method, customer, products);

            orderProducer.sendOrderConfirmation(confirmation);
            log.info("[OrderSagaConsumer] OrderConfirmation sent to order-topic: correlationId=[{}]",
                    event.correlationId());
        } catch (Exception ex) {
            log.warn("[OrderSagaConsumer] Non-blocking failure: Failed to send OrderConfirmation for correlationId=[{}]. Error: {}",
                    event.correlationId(), ex.getMessage());
        }
    }
}
