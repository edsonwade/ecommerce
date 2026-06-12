package code.with.vanilson.orderservice.kafka;

import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.event.OrderStatusChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderSagaConsumer consumer;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class)))
                .thenReturn(mock(io.micrometer.core.instrument.Counter.class));
    }

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

        @Test
        @DisplayName("should publish OrderStatusChangedEvent with CONFIRMED status")
        void shouldPublishConfirmedEvent() {
            consumer.onPaymentAuthorized(buildEvent(), 0, 0L, acknowledgment);

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            OrderStatusChangedEvent published = captor.getValue();
            assertThat(published.correlationId()).isEqualTo(CORRELATION_ID);
            assertThat(published.status()).isEqualTo("CONFIRMED");
            assertThat(published.orderReference()).isEqualTo("ORD-001");
            assertThat(published.occurredAt()).isNotNull();
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

        @Test
        @DisplayName("should publish OrderStatusChangedEvent with CANCELLED status")
        void shouldPublishCancelledEvent() {
            PaymentFailedEvent event = new PaymentFailedEvent(
                    "evt-fail-002", CORRELATION_ID, "ORD-001",
                    "Card declined", Instant.now(), 1);

            consumer.onPaymentFailed(event, 0, 0L, acknowledgment);

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            OrderStatusChangedEvent published = captor.getValue();
            assertThat(published.correlationId()).isEqualTo(CORRELATION_ID);
            assertThat(published.status()).isEqualTo("CANCELLED");
            assertThat(published.orderReference()).isEqualTo("ORD-001");
        }
    }

    @Nested
    @DisplayName("onInventoryInsufficient — cancellation")
    class InventoryInsufficient {

        @Test
        @DisplayName("should cancel order directly with a single status update")
        void shouldCancelOrderOnInsufficientStock() {
            InventoryInsufficientEvent event = new InventoryInsufficientEvent(
                    "evt-inv-001", CORRELATION_ID, "ORD-001",
                    1, 5.0, 0.0, Instant.now(), 1);

            consumer.onInventoryInsufficient(event, 0, 0L, acknowledgment);

            verify(orderService).updateStatus(CORRELATION_ID, OrderStatus.CANCELLED);
            verify(orderService, never()).updateStatus(CORRELATION_ID, OrderStatus.INVENTORY_INSUFFICIENT);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should publish a single CANCELLED event")
        void shouldPublishSingleTerminalEvent() {
            InventoryInsufficientEvent event = new InventoryInsufficientEvent(
                    "evt-inv-002", CORRELATION_ID, "ORD-001",
                    1, 5.0, 0.0, Instant.now(), 1);

            consumer.onInventoryInsufficient(event, 0, 0L, acknowledgment);

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            List<OrderStatusChangedEvent> published = captor.getAllValues();
            assertThat(published).hasSize(1)
                    .extracting(OrderStatusChangedEvent::status)
                    .containsExactly("CANCELLED");
        }
    }
}
