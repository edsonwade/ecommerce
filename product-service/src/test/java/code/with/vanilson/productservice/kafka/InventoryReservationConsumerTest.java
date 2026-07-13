package code.with.vanilson.productservice.kafka;

import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import code.with.vanilson.productservice.InventoryReservationService;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.category.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.lenient;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryReservationConsumerTest — Unit Tests (Phase 3 — Saga Step 1)
 * <p>
 * Validates that the consumer:
 * 1. Reserves stock correctly when available
 * 2. Publishes inventory.reserved on success
 * 3. Publishes inventory.insufficient when stock is not enough
 * 4. Commits Kafka offset (ack.acknowledge()) in both paths
 * 5. Decrements stock correctly in the DB
 * <p>
 * Framework: JUnit 5 + Mockito + AssertJ.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReservationConsumer — Saga Step 1 Unit Tests")
class InventoryReservationConsumerTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private MessageSource messageSource;
    @Mock
    private Acknowledgment acknowledgment;
    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private InventoryReservationConsumer consumer;

    private static final String CORRELATION_ID = "corr-test-001";
    private static final String ORDER_REFERENCE = "ORD-SAGA-001";
    private static final String TOPIC_RESERVED = "inventory.reserved";
    private static final String TOPIC_INSUFFICIENT = "inventory.insufficient";

    private Product laptop;
    private OrderRequestedEvent validEvent;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        lenient().when(meterRegistry.counter(anyString(), any(String[].class)))
                .thenReturn(org.mockito.Mockito.mock(io.micrometer.core.instrument.Counter.class));

        // B4 idempotency guard runs first on every delivery — default to "no prior
        // reservations" so the pre-existing scenarios exercise the first-delivery path.
        lenient().when(reservationRepository.findByCorrelationId(anyString()))
                .thenReturn(List.of());

        // Real shared reservation core wired over the mocked repository — the
        // consumer test exercises the actual fetch/validate/deduct logic.
        // TransactionTemplate over a mocked manager: runs the callback inline
        // and rethrows on failure, mirroring the production rollback semantics.
        consumer = new InventoryReservationConsumer(
                new InventoryReservationService(productRepository, messageSource),
                reservationRepository, productRepository, kafkaTemplate, meterRegistry,
                new org.springframework.transaction.support.TransactionTemplate(transactionManager));

        Category category = Category.builder().id(1).name("Electronics").description("Electronic items").build();
        laptop = new Product(1, "Laptop", "Gaming Laptop", 10.0, BigDecimal.valueOf(1200.00), category);

        validEvent = new OrderRequestedEvent(
                "evt-001",
                CORRELATION_ID,
                "cust-001",
                "ana@example.com",
                "Ana",
                "Silva",
                List.of(new OrderRequestedEvent.ProductPurchaseItem(1, 3.0)),
                BigDecimal.valueOf(3600.00),
                "CREDIT_CARD",
                ORDER_REFERENCE,
                "tenant-test-001",
                42,
                Instant.now(),
                1
        );
    }

    // -------------------------------------------------------
    // Happy path — sufficient stock
    // -------------------------------------------------------

    @Nested
    @DisplayName("onOrderRequested — sufficient stock (happy path)")
    class HappyPath {

        @Test
        @DisplayName("should publish inventory.reserved when stock is available")
        void shouldPublishInventoryReservedOnSuccess() {
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(validEvent, 0, 0L, acknowledgment);

            verify(kafkaTemplate).send(eq(TOPIC_RESERVED), eq(CORRELATION_ID), any());
        }

        @Test
        @DisplayName("should decrement stock by requested quantity")
        void shouldDecrementStockCorrectly() {
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(validEvent, 0, 0L, acknowledgment);

            // laptop had 10.0, requested 3.0 → expected 7.0
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertThat(captor.getValue().getAvailableQuantity())
                    .as("Stock must be decremented from 10 to 7")
                    .isEqualTo(7.0);
        }

        @Test
        @DisplayName("should save InventoryReservation record on successful reservation")
        void shouldSaveReservationRecordOnSuccess() {
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(validEvent, 0, 0L, acknowledgment);

            ArgumentCaptor<InventoryReservation> captor = ArgumentCaptor.forClass(InventoryReservation.class);
            verify(reservationRepository).save(captor.capture());
            InventoryReservation saved = captor.getValue();
            assertThat(saved.getCorrelationId()).isEqualTo(CORRELATION_ID);
            assertThat(saved.getProductId()).isEqualTo(1);
            assertThat(saved.getReservedQuantity()).isEqualTo(3);
            assertThat(saved.getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RESERVED);
        }

        @Test
        @DisplayName("should acknowledge Kafka offset after successful reservation")
        void shouldAcknowledgeOffsetOnSuccess() {
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(validEvent, 0, 0L, acknowledgment);

            verify(acknowledgment, times(1)).acknowledge();
        }
    }

    // -------------------------------------------------------
    // Compensation path — insufficient stock
    // -------------------------------------------------------

    @Nested
    @DisplayName("onOrderRequested — insufficient stock (compensation)")
    class CompensationPath {

        @Test
        @DisplayName("should publish inventory.insufficient when stock is not enough")
        void shouldPublishInventoryInsufficientWhenStockLow() {
            // laptop has 10.0 but we request 20.0 → insufficient
            OrderRequestedEvent overRequest = new OrderRequestedEvent(
                    "evt-002", CORRELATION_ID, "cust-001", "ana@example.com",
                    "Ana", "Silva",
                    List.of(new OrderRequestedEvent.ProductPurchaseItem(1, 20.0)),
                    BigDecimal.valueOf(24000.00), "CREDIT_CARD",
                    ORDER_REFERENCE, "tenant-test-001", 42, Instant.now(), 1
            );
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(overRequest, 0, 0L, acknowledgment);

            verify(kafkaTemplate).send(eq(TOPIC_INSUFFICIENT), eq(CORRELATION_ID), any());
        }

        @Test
        @DisplayName("should NOT publish inventory.reserved when stock insufficient")
        void shouldNotPublishReservedOnInsufficientStock() {
            OrderRequestedEvent overRequest = new OrderRequestedEvent(
                    "evt-003", CORRELATION_ID, "cust-001", "ana@example.com",
                    "Ana", "Silva",
                    List.of(new OrderRequestedEvent.ProductPurchaseItem(1, 999.0)),
                    BigDecimal.ZERO, "CREDIT_CARD",
                    ORDER_REFERENCE, "tenant-test-001", 42, Instant.now(), 1
            );
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(overRequest, 0, 0L, acknowledgment);

            // Must NOT publish reserved
            verify(kafkaTemplate, times(0)).send(eq(TOPIC_RESERVED), anyString(), any());
        }

        @Test
        @DisplayName("should NOT decrement stock when insufficient")
        void shouldNotDecrementStockWhenInsufficient() {
            OrderRequestedEvent overRequest = new OrderRequestedEvent(
                    "evt-004", CORRELATION_ID, "cust-001", "ana@example.com",
                    "Ana", "Silva",
                    List.of(new OrderRequestedEvent.ProductPurchaseItem(1, 50.0)),
                    BigDecimal.ZERO, "CREDIT_CARD",
                    ORDER_REFERENCE, "tenant-test-001", 42, Instant.now(), 1
            );
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(overRequest, 0, 0L, acknowledgment);

            verify(productRepository, times(0)).save(any());
        }

        @Test
        @DisplayName("should publish event carrying the failing product's id, name and quantities")
        void shouldPublishEventWithRealProductDetails() {
            // laptop (id=1, "Laptop") has 10.0 available but 20.0 requested
            OrderRequestedEvent overRequest = new OrderRequestedEvent(
                    "evt-006", CORRELATION_ID, "cust-001", "ana@example.com",
                    "Ana", "Silva",
                    List.of(new OrderRequestedEvent.ProductPurchaseItem(1, 20.0)),
                    BigDecimal.valueOf(24000.00), "CREDIT_CARD",
                    ORDER_REFERENCE, "tenant-test-001", 42, Instant.now(), 1
            );
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(overRequest, 0, 0L, acknowledgment);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(kafkaTemplate).send(eq(TOPIC_INSUFFICIENT), eq(CORRELATION_ID), captor.capture());

            InventoryInsufficientEvent published = (InventoryInsufficientEvent) captor.getValue();
            assertThat(published.productId()).isEqualTo(1);
            assertThat(published.productName()).isEqualTo("Laptop");
            assertThat(published.requestedQty()).isEqualTo(20.0);
            assertThat(published.availableQty()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Fase 3: SUSPENDED product → inventory.insufficient published, stock untouched, offset acked")
        void suspendedProductPublishesInsufficientAndDeductsNothing() {
            // The product EXISTS and has plenty of stock — but it is suspended. The
            // consumer must FIND it (not 404 it) and cancel the saga with the same
            // insufficient event, carrying the real product details.
            laptop.setStatus(code.with.vanilson.productservice.ProductStatus.SUSPENDED);
            OrderRequestedEvent request = new OrderRequestedEvent(
                    "evt-007", CORRELATION_ID, "cust-001", "ana@example.com",
                    "Ana", "Silva",
                    List.of(new OrderRequestedEvent.ProductPurchaseItem(1, 2.0)),
                    BigDecimal.valueOf(2400.00), "CREDIT_CARD",
                    ORDER_REFERENCE, "tenant-test-001", 42, Instant.now(), 1
            );
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(request, 0, 0L, acknowledgment);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(kafkaTemplate).send(eq(TOPIC_INSUFFICIENT), eq(CORRELATION_ID), captor.capture());
            InventoryInsufficientEvent published = (InventoryInsufficientEvent) captor.getValue();
            assertThat(published.productId()).isEqualTo(1);
            assertThat(published.productName()).isEqualTo("Laptop");

            // No deduction, no reserved event, offset committed.
            verify(productRepository, times(0)).save(any());
            verify(kafkaTemplate, times(0)).send(eq(TOPIC_RESERVED), anyString(), any());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("should acknowledge Kafka offset even on insufficient stock (no requeue)")
        void shouldAcknowledgeOffsetOnInsufficientStock() {
            OrderRequestedEvent overRequest = new OrderRequestedEvent(
                    "evt-005", CORRELATION_ID, "cust-001", "ana@example.com",
                    "Ana", "Silva",
                    List.of(new OrderRequestedEvent.ProductPurchaseItem(1, 999.0)),
                    BigDecimal.ZERO, "CREDIT_CARD",
                    ORDER_REFERENCE, "tenant-test-001", 42, Instant.now(), 1
            );
            when(productRepository.findAllByIdInOrderById(List.of(1)))
                    .thenReturn(List.of(laptop));

            consumer.onOrderRequested(overRequest, 0, 0L, acknowledgment);

            // Offset must still be acknowledged — DLQ handles retries, not requeue
            verify(acknowledgment, times(1)).acknowledge();
        }
    }

    // -------------------------------------------------------
    // Idempotency guard (B4) — duplicate delivery
    // -------------------------------------------------------

    @Nested
    @DisplayName("onOrderRequested — duplicate delivery (B4 idempotency guard)")
    class DuplicateDelivery {

        private InventoryReservation reservedRow() {
            return InventoryReservation.builder()
                    .id(1L)
                    .correlationId(CORRELATION_ID)
                    .productId(1)
                    .reservedQuantity(3)
                    .status(InventoryReservation.ReservationStatus.RESERVED)
                    .build();
        }

        private InventoryReservation releasedRow() {
            return InventoryReservation.builder()
                    .id(2L)
                    .correlationId(CORRELATION_ID)
                    .productId(1)
                    .reservedQuantity(3)
                    .status(InventoryReservation.ReservationStatus.RELEASED)
                    .build();
        }

        @Test
        @DisplayName("should NOT deduct stock again when the same correlationId was already reserved")
        void shouldNotDeductStockOnDuplicateDelivery() {
            when(reservationRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(List.of(reservedRow()));
            when(productRepository.findAllById(List.of(1))).thenReturn(List.of(laptop));

            consumer.onOrderRequested(validEvent, 0, 1L, acknowledgment);

            // The reservation core must never run: no locked fetch, no stock save,
            // no second reservation row.
            verify(productRepository, times(0)).findAllByIdInOrderById(any());
            verify(productRepository, times(0)).save(any());
            verify(reservationRepository, times(0)).save(any());
        }

        @Test
        @DisplayName("should re-publish inventory.reserved rebuilt from the persisted rows on duplicate")
        void shouldRepublishReservedOnDuplicate() {
            when(reservationRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(List.of(reservedRow()));
            when(productRepository.findAllById(List.of(1))).thenReturn(List.of(laptop));

            consumer.onOrderRequested(validEvent, 0, 1L, acknowledgment);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(kafkaTemplate).send(eq(TOPIC_RESERVED), eq(CORRELATION_ID), captor.capture());
            InventoryReservedEvent republished = (InventoryReservedEvent) captor.getValue();
            assertThat(republished.correlationId()).isEqualTo(CORRELATION_ID);
            assertThat(republished.reservedItems()).hasSize(1);
            assertThat(republished.reservedItems().get(0).productId()).isEqualTo(1);
            // Quantity must come from the persisted reservation row, not the event
            assertThat(republished.reservedItems().get(0).quantity()).isEqualTo(3.0);
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("should NOT re-publish inventory.reserved when reservations were already RELEASED (saga compensated)")
        void shouldNotRepublishWhenAlreadyReleased() {
            when(reservationRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(List.of(releasedRow()));

            consumer.onOrderRequested(validEvent, 0, 1L, acknowledgment);

            // Re-publishing inventory.reserved would re-trigger payment for a dead order
            verify(kafkaTemplate, times(0)).send(anyString(), anyString(), any());
            verify(productRepository, times(0)).save(any());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("should still acknowledge the duplicate offset so it is not redelivered forever")
        void shouldAcknowledgeDuplicateOffset() {
            when(reservationRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(List.of(reservedRow()));
            when(productRepository.findAllById(List.of(1))).thenReturn(List.of(laptop));

            consumer.onOrderRequested(validEvent, 0, 1L, acknowledgment);

            verify(acknowledgment, times(1)).acknowledge();
        }
    }
}
