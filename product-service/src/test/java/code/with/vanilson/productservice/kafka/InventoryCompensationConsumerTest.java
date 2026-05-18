package code.with.vanilson.productservice.kafka;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryCompensationConsumer — Saga Compensation Unit Tests")
class InventoryCompensationConsumerTest {

    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private InventoryCompensationConsumer consumer;

    private static final String CORRELATION_ID = "corr-comp-001";

    private PaymentFailedEvent buildPaymentFailedEvent() {
        return new PaymentFailedEvent(
                "evt-fail-001", CORRELATION_ID, "ORD-COMP-001",
                "Insufficient funds", Instant.now(), 1);
    }

    @Nested
    @DisplayName("onPaymentFailed — stock release")
    class StockRelease {

        @Test
        @DisplayName("should restore stock and mark reservation as RELEASED")
        void shouldRestoreStockAndMarkReleased() {
            Product laptop = new Product(1, "Laptop", "Gaming Laptop", 7.0, BigDecimal.valueOf(1200));
            InventoryReservation reservation = InventoryReservation.builder()
                    .id(1L).correlationId(CORRELATION_ID).productId(1)
                    .reservedQuantity(3).status(InventoryReservation.ReservationStatus.RESERVED)
                    .createdAt(LocalDateTime.now()).build();

            when(reservationRepository.findByCorrelationIdAndStatus(
                    CORRELATION_ID, InventoryReservation.ReservationStatus.RESERVED))
                    .thenReturn(List.of(reservation));
            when(productRepository.findById(1)).thenReturn(Optional.of(laptop));

            consumer.onPaymentFailed(buildPaymentFailedEvent(), 0, 0L, acknowledgment);

            assertThat(laptop.getAvailableQuantity()).isEqualTo(10.0);
            assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RELEASED);
            assertThat(reservation.getReleasedAt()).isNotNull();
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should publish inventory.released event after compensation")
        void shouldPublishInventoryReleasedEvent() {
            Product laptop = new Product(1, "Laptop", "Gaming Laptop", 7.0, BigDecimal.valueOf(1200));
            InventoryReservation reservation = InventoryReservation.builder()
                    .id(1L).correlationId(CORRELATION_ID).productId(1)
                    .reservedQuantity(3).status(InventoryReservation.ReservationStatus.RESERVED)
                    .createdAt(LocalDateTime.now()).build();

            when(reservationRepository.findByCorrelationIdAndStatus(
                    CORRELATION_ID, InventoryReservation.ReservationStatus.RESERVED))
                    .thenReturn(List.of(reservation));
            when(productRepository.findById(1)).thenReturn(Optional.of(laptop));

            consumer.onPaymentFailed(buildPaymentFailedEvent(), 0, 0L, acknowledgment);

            verify(kafkaTemplate).send(eq("inventory.released"), eq(CORRELATION_ID), any());
        }
    }

    @Nested
    @DisplayName("onPaymentFailed — idempotency")
    class Idempotency {

        @Test
        @DisplayName("should be idempotent when no RESERVED records exist (already released)")
        void shouldBeIdempotentWhenAlreadyReleased() {
            when(reservationRepository.findByCorrelationIdAndStatus(
                    CORRELATION_ID, InventoryReservation.ReservationStatus.RESERVED))
                    .thenReturn(List.of());

            consumer.onPaymentFailed(buildPaymentFailedEvent(), 0, 0L, acknowledgment);

            verify(productRepository, never()).findById(any());
            verify(productRepository, never()).save(any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should not publish inventory.released when no reservations to release")
        void shouldNotPublishReleasedWhenNothingToRelease() {
            when(reservationRepository.findByCorrelationIdAndStatus(
                    CORRELATION_ID, InventoryReservation.ReservationStatus.RESERVED))
                    .thenReturn(List.of());

            consumer.onPaymentFailed(buildPaymentFailedEvent(), 0, 0L, acknowledgment);

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }
    }
}
