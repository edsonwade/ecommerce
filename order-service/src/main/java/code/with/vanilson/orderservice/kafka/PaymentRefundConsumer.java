package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.orderservice.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * PaymentRefundConsumer — Infrastructure Layer (Kafka Consumer, Fase 6).
 * <p>
 * Consumes {@code payment.refunded} (published by payment-service after
 * {@code PaymentService.refundPayment}) and applies the refund to the owning order via
 * {@link OrderService#applyRefund}. Kept as its OWN class (SRP) rather than folded into
 * {@link OrderSagaConsumer} — refunds are a separate lifecycle from the choreography saga
 * that consumer drives (REQUESTED → CONFIRMED/CANCELLED); this handles the post-confirmation
 * REFUNDED transition.
 * <p>
 * Manual acknowledgement, same discipline as the saga consumer: offset is committed only
 * after {@code applyRefund}'s transaction (status update + outbox row) succeeds. If the
 * order can't transition (e.g. already CANCELLED), {@code applyRefund} throws, {@code ack}
 * is never called, and Kafka's retry/DLQ error handler on {@code sagaKafkaListenerContainerFactory}
 * takes over — the event lands on {@code payment.refunded.DLQ} after 3 retries (documented
 * Fase 6 failure semantics: the payment stays REFUNDED regardless).
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundConsumer {

    private final OrderService orderService;

    @KafkaListener(
            topics = "payment.refunded",
            groupId = "order-saga-group",
            containerFactory = "sagaKafkaListenerContainerFactory")
    public void onPaymentRefunded(
            @Payload PaymentRefundedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            MDC.put("sagaStep", "order-refund");
            MDC.put("orderId", String.valueOf(event.orderId()));

            log.info("[PaymentRefundConsumer] payment.refunded received: orderId=[{}] paymentId=[{}] " +
                            "partition=[{}] offset=[{}]",
                    event.orderId(), event.paymentId(), partition, offset);

            orderService.applyRefund(event.orderId(), event.eventId(), event.occurredAt());

            log.info("[PaymentRefundConsumer] Refund applied: orderId=[{}]", event.orderId());
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }
}
