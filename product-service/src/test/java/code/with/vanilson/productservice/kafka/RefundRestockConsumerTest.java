package code.with.vanilson.productservice.kafka;

import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RefundRestockConsumerTest — Fase 6 (basic refunds).
 * <p>
 * Mirrors {@code InventoryCompensationConsumerTest} exactly (same restock mechanics,
 * different trigger topic) — restock once, idempotent when already RELEASED, no follow-up
 * event published (restock is terminal, unlike the compensation path).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundRestockConsumer — Fase 6 Refund Restock Unit Tests")
class RefundRestockConsumerTest {

    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private ProductRepository productRepository;
    @Mock private Acknowledgment acknowledgment;
    @Mock private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @InjectMocks
    private RefundRestockConsumer consumer;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class)))
                .thenReturn(mock(io.micrometer.core.instrument.Counter.class));
    }

    private static final String CORRELATION_ID = "corr-refund-001";

    private OrderRefundedEvent buildEvent() {
        return new OrderRefundedEvent("evt-refund-001", CORRELATION_ID, "ORD-REFUND-001", Instant.now(), 1);
    }

    @Nested
    @DisplayName("onOrderRefunded — restock")
    class Restock {

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

            consumer.onOrderRefunded(buildEvent(), 0, 0L, acknowledgment);

            assertThat(laptop.getAvailableQuantity()).isEqualTo(10.0);
            assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RELEASED);
            assertThat(reservation.getReleasedAt()).isNotNull();
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should restock every reserved line for the order, not just the first")
        void shouldRestockEveryLine() {
            Product laptop = new Product(1, "Laptop", "Gaming Laptop", 7.0, BigDecimal.valueOf(1200));
            Product mouse  = new Product(2, "Mouse", "Wireless Mouse", 20.0, BigDecimal.valueOf(30));
            InventoryReservation r1 = InventoryReservation.builder()
                    .id(1L).correlationId(CORRELATION_ID).productId(1)
                    .reservedQuantity(2).status(InventoryReservation.ReservationStatus.RESERVED).build();
            InventoryReservation r2 = InventoryReservation.builder()
                    .id(2L).correlationId(CORRELATION_ID).productId(2)
                    .reservedQuantity(5).status(InventoryReservation.ReservationStatus.RESERVED).build();

            when(reservationRepository.findByCorrelationIdAndStatus(
                    CORRELATION_ID, InventoryReservation.ReservationStatus.RESERVED))
                    .thenReturn(List.of(r1, r2));
            when(productRepository.findById(1)).thenReturn(Optional.of(laptop));
            when(productRepository.findById(2)).thenReturn(Optional.of(mouse));

            consumer.onOrderRefunded(buildEvent(), 0, 0L, acknowledgment);

            assertThat(laptop.getAvailableQuantity()).isEqualTo(9.0);
            assertThat(mouse.getAvailableQuantity()).isEqualTo(25.0);
            assertThat(r1.getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RELEASED);
            assertThat(r2.getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RELEASED);
        }
    }

    @Nested
    @DisplayName("onOrderRefunded — idempotency")
    class Idempotency {

        @Test
        @DisplayName("should be idempotent when no RESERVED records exist (already released)")
        void shouldBeIdempotentWhenAlreadyReleased() {
            when(reservationRepository.findByCorrelationIdAndStatus(
                    CORRELATION_ID, InventoryReservation.ReservationStatus.RESERVED))
                    .thenReturn(List.of());

            consumer.onOrderRefunded(buildEvent(), 0, 0L, acknowledgment);

            verify(productRepository, never()).findById(any());
            verify(productRepository, never()).save(any());
            verify(acknowledgment).acknowledge();
        }
    }
}
