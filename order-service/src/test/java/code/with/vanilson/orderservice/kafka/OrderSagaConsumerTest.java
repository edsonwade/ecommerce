package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaConsumer — Saga Status Update + Notification Tests")
class OrderSagaConsumerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private OrderProducer orderProducer;
    @Mock
    private MessageSource messageSource;
    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private OrderSagaConsumer consumer;

    private static final String CORRELATION_ID = "corr-saga-001";

    @Nested
    @DisplayName("onPaymentAuthorized — happy path")
    class PaymentAuthorized {

        private PaymentAuthorizedEvent buildEvent() {
            return new PaymentAuthorizedEvent(
                    "evt-001", CORRELATION_ID, "ORD-001", 42,
                    "cust-001", "ana@example.com", "Ana", "Silva",
                    List.of(new PaymentAuthorizedEvent.ReservedItem(
                            1, "Laptop", 2.0, BigDecimal.valueOf(1200))),
                    BigDecimal.valueOf(2400), "CREDIT_CARD",
                    Instant.now(), 2);
        }

        @Test
        @DisplayName("should update order status to CONFIRMED")
        void shouldUpdateOrderStatusToConfirmed() {
            consumer.onPaymentAuthorized(buildEvent(), 0, 0L, acknowledgment);

            verify(orderService).updateStatus(CORRELATION_ID, OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("should send OrderConfirmation to order-topic via OrderProducer")
        void shouldSendOrderConfirmation() {
            consumer.onPaymentAuthorized(buildEvent(), 0, 0L, acknowledgment);

            ArgumentCaptor<OrderConfirmation> captor = ArgumentCaptor.forClass(OrderConfirmation.class);
            verify(orderProducer).sendOrderConfirmation(captor.capture());

            OrderConfirmation confirmation = captor.getValue();
            assertThat(confirmation.orderReference()).isEqualTo("ORD-001");
            assertThat(confirmation.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2400));
            assertThat(confirmation.customer().customerId()).isEqualTo("cust-001");
            assertThat(confirmation.customer().email()).isEqualTo("ana@example.com");
            assertThat(confirmation.products()).hasSize(1);
            assertThat(confirmation.products().get(0).name()).isEqualTo("Laptop");
        }

        @Test
        @DisplayName("should acknowledge Kafka offset")
        void shouldAcknowledgeOffset() {
            consumer.onPaymentAuthorized(buildEvent(), 0, 0L, acknowledgment);

            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should still confirm order even if notification fails")
        void shouldConfirmOrderEvenIfNotificationFails() {
            doThrow(new RuntimeException("Kafka send failed"))
                    .when(orderProducer).sendOrderConfirmation(any());

            consumer.onPaymentAuthorized(buildEvent(), 0, 0L, acknowledgment);

            verify(orderService).updateStatus(CORRELATION_ID, OrderStatus.CONFIRMED);
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("onPaymentFailed — cancellation")
    class PaymentFailed {

        @Test
        @DisplayName("should update order status to CANCELLED")
        void shouldCancelOrder() {
            PaymentFailedEvent event = new PaymentFailedEvent(
                    "evt-fail-001", CORRELATION_ID, "ORD-001",
                    "Insufficient funds", Instant.now(), 1);

            consumer.onPaymentFailed(event, 0, 0L, acknowledgment);

            verify(orderService).updateStatus(CORRELATION_ID, OrderStatus.CANCELLED);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should NOT send OrderConfirmation on payment failure")
        void shouldNotSendNotificationOnFailure() {
            PaymentFailedEvent event = new PaymentFailedEvent(
                    "evt-fail-001", CORRELATION_ID, "ORD-001",
                    "Insufficient funds", Instant.now(), 1);

            consumer.onPaymentFailed(event, 0, 0L, acknowledgment);

            verify(orderProducer, never()).sendOrderConfirmation(any());
        }
    }

    @Nested
    @DisplayName("onInventoryInsufficient — cancellation")
    class InventoryInsufficient {

        @Test
        @DisplayName("should update order through INVENTORY_INSUFFICIENT to CANCELLED")
        void shouldCancelOrderOnInsufficientStock() {
            InventoryInsufficientEvent event = new InventoryInsufficientEvent(
                    "evt-inv-001", CORRELATION_ID, "ORD-001",
                    1, 5.0, 0.0, Instant.now(), 1);

            consumer.onInventoryInsufficient(event, 0, 0L, acknowledgment);

            verify(orderService).updateStatus(CORRELATION_ID, OrderStatus.INVENTORY_INSUFFICIENT);
            verify(orderService).updateStatus(CORRELATION_ID, OrderStatus.CANCELLED);
            verify(acknowledgment).acknowledge();
        }
    }
}
