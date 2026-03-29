package code.with.vanilson.paymentservice.infrastructure.kafka;

import code.with.vanilson.paymentservice.application.PaymentRequest;
import code.with.vanilson.paymentservice.application.PaymentService;
import code.with.vanilson.paymentservice.domain.CustomerData;
import code.with.vanilson.paymentservice.domain.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * PaymentSagaConsumer — Infrastructure Layer (Saga Step 2)
 * <p>
 * Consumes inventory.reserved events and processes payment.
 * <p>
 * Saga choreography — payment-service's role:
 * 1. Consume inventory.reserved (stock was reserved by product-service)
 * 2. Process payment (idempotent — same orderReference = same result)
 * 3a. SUCCESS → publish payment.authorized → order-service confirms
 * 3b. FAILURE → publish payment.failed → order-service cancels,
 *               product-service releases stock
 * <p>
 * Idempotency: PaymentService.createPayment() already handles duplicates
 * via idempotencyKey. If the same inventory.reserved event arrives twice,
 * the second payment attempt returns the existing paymentId — no double charge.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSagaConsumer {

    private final PaymentService                paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_AUTHORIZED = "payment.authorized";
    private static final String TOPIC_FAILED     = "payment.failed";

    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "payment-saga-group",
            containerFactory = "paymentSagaKafkaListenerContainerFactory")
    public void onInventoryReserved(
            @Payload InventoryReservedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[PaymentSagaConsumer] inventory.reserved received: correlationId=[{}] amount=[{}] partition=[{}] offset=[{}]",
                event.correlationId(), event.totalAmount(), partition, offset);

        try {
            // Map to PaymentRequest — uses local domain types
            PaymentMethod method = mapPaymentMethod(event.paymentMethod());
            CustomerData  customer = new CustomerData(
                    event.customerId(), event.customerFirstname(),
                    event.customerLastname(), event.customerEmail());

            PaymentRequest paymentRequest = new PaymentRequest(
                    null,
                    event.totalAmount(),
                    method,
                    null,            // orderId not available here — set null, saga uses correlationId
                    event.orderReference(),
                    customer
            );

            Integer paymentId = paymentService.createPayment(paymentRequest);

            publishPaymentAuthorized(event, paymentId);
            log.info("[PaymentSagaConsumer] Payment authorized: correlationId=[{}] paymentId=[{}]",
                    event.correlationId(), paymentId);

        } catch (Exception ex) {
            log.error("[PaymentSagaConsumer] Payment failed: correlationId=[{}] reason=[{}]",
                    event.correlationId(), ex.getMessage());
            publishPaymentFailed(event, ex.getMessage());
        }

        ack.acknowledge();
    }

    // -------------------------------------------------------

    private void publishPaymentAuthorized(InventoryReservedEvent event, Integer paymentId) {
        PaymentAuthorizedEvent authorized = new PaymentAuthorizedEvent(
                UUID.randomUUID().toString(),
                event.correlationId(),
                event.orderReference(),
                paymentId,
                Instant.now(),
                1
        );
        kafkaTemplate.send(TOPIC_AUTHORIZED, event.correlationId(), authorized);
    }

    private void publishPaymentFailed(InventoryReservedEvent event, String reason) {
        PaymentFailedEvent failed = new PaymentFailedEvent(
                UUID.randomUUID().toString(),
                event.correlationId(),
                event.orderReference(),
                reason,
                Instant.now(),
                1
        );
        kafkaTemplate.send(TOPIC_FAILED, event.correlationId(), failed);
    }

    private PaymentMethod mapPaymentMethod(String method) {
        try {
            return PaymentMethod.valueOf(method.toUpperCase());
        } catch (Exception ex) {
            log.warn("[PaymentSagaConsumer] Unknown payment method '{}', defaulting to CREDIT_CARD", method);
            return PaymentMethod.CREDIT_CARD;
        }
    }
}
