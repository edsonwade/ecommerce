package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.exception.OrderIllegalStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PaymentRefundConsumerTest — Fase 6.
 * <p>
 * Pins the manual-ack discipline: {@code ack.acknowledge()} fires only when
 * {@code OrderService.applyRefund} succeeds; on failure the exception propagates
 * un-caught (no ack) so Kafka's retry/DLQ handler takes over — the same contract
 * {@code OrderSagaConsumer} uses for the choreography saga.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRefundConsumer — Fase 6 (unit)")
class PaymentRefundConsumerTest {

    @Mock private OrderService orderService;
    @Mock private Acknowledgment acknowledgment;

    @InjectMocks
    private PaymentRefundConsumer consumer;

    private PaymentRefundedEvent buildEvent() {
        return new PaymentRefundedEvent(
                "evt-refund-001", 42, 100, "ORD-REFUND-1",
                BigDecimal.valueOf(150.00), Instant.now(), 1);
    }

    @Test
    @DisplayName("applies the refund via OrderService and acknowledges on success")
    void appliesRefundAndAcknowledges() {
        PaymentRefundedEvent event = buildEvent();

        consumer.onPaymentRefunded(event, 0, 0L, acknowledgment);

        verify(orderService).applyRefund(eq(100), eq("evt-refund-001"), eq(event.occurredAt()));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("does NOT acknowledge when applyRefund throws (illegal transition) — Kafka retries/DLQs")
    void doesNotAcknowledgeOnFailure() {
        PaymentRefundedEvent event = buildEvent();
        doThrow(new OrderIllegalStateTransitionException("illegal", "order.status.transition.invalid"))
                .when(orderService).applyRefund(eq(100), eq("evt-refund-001"), eq(event.occurredAt()));

        assertThatThrownBy(() -> consumer.onPaymentRefunded(event, 0, 0L, acknowledgment))
                .isInstanceOf(OrderIllegalStateTransitionException.class);

        verify(acknowledgment, never()).acknowledge();
    }
}
