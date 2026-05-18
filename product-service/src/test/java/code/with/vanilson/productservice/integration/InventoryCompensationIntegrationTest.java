package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.kafka.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InventoryCompensationIntegrationTest — Integration tests for inventory release compensation (Phase 0)
 * <p>
 * Tests the inventory compensation flow:
 * 1. InventoryReservation created when order.requested arrives
 * 2. When payment.failed arrives, InventoryCompensationConsumer releases stock
 * 3. InventoryReservation marked as RELEASED with timestamp
 * 4. inventory.released event published
 * <p>
 * Uses:
 * - H2 in-memory database (via test profile) for real persistence
 * - EmbeddedKafka for real event flow
 * - No mocks for Kafka/database
 * </p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=test-inventory-compensation"
        })
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {
                "auto.create.topics.enable=true",
                "log.retention.hours=1"
        })
@ActiveProfiles("test")
@DisplayName("Inventory Compensation Integration Tests (Phase 0)")
class InventoryCompensationIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired InventoryReservationRepository reservationRepository;
    @Autowired KafkaTemplate<String, Object> kafkaTemplate;

    private Product laptop;
    private Product headphones;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        reservationRepository.deleteAll();

        laptop = Product.builder()
                .id(1)
                .name("Laptop")
                .description("Gaming Laptop")
                .availableQuantity(10.0)
                .price(BigDecimal.valueOf(1200))
                .build();
        productRepository.save(laptop);

        headphones = Product.builder()
                .id(2)
                .name("Headphones")
                .description("Wireless Headphones")
                .availableQuantity(50.0)
                .price(BigDecimal.valueOf(150))
                .build();
        productRepository.save(headphones);
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    @Nested
    @DisplayName("Inventory Compensation — Stock Release")
    class StockRelease {

        @Test
        @DisplayName("should release stock when payment.failed event received")
        void shouldReleaseStockOnPaymentFailed() throws Exception {
            // Setup: create a reservation to simulate prior stock reservation
            String correlationId = "corr-comp-001";
            InventoryReservation reservation = InventoryReservation.builder()
                    .correlationId(correlationId)
                    .productId(1)
                    .reservedQuantity(3)
                    .status(InventoryReservation.ReservationStatus.RESERVED)
                    .createdAt(LocalDateTime.now())
                    .build();
            reservationRepository.save(reservation);

            // Verify initial state
            Product beforeLaptop = productRepository.findById(1).orElseThrow();
            assertThat(beforeLaptop.getAvailableQuantity()).isEqualTo(10.0);

            // Act: publish payment.failed event to trigger compensation
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    "evt-fail-001", correlationId, "ORD-FAIL-001",
                    "Insufficient funds", java.time.Instant.now(), 1
            );
            kafkaTemplate.send("payment.failed", correlationId, failedEvent);

            // Wait for compensation consumer to process
            Thread.sleep(2000);

            // Assert: stock should be restored
            Product afterLaptop = productRepository.findById(1).orElseThrow();
            assertThat(afterLaptop.getAvailableQuantity()).isEqualTo(13.0);

            // Assert: reservation should be marked RELEASED
            InventoryReservation releasedReservation = reservationRepository
                    .findByCorrelationIdAndStatus(correlationId, InventoryReservation.ReservationStatus.RELEASED)
                    .stream()
                    .filter(r -> r.getProductId() == 1)
                    .findFirst()
                    .orElseThrow();

            assertThat(releasedReservation.getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RELEASED);
            assertThat(releasedReservation.getReleasedAt()).isNotNull();
        }

        @Test
        @DisplayName("should release multiple products when payment fails for multi-item order")
        void shouldReleaseMultipleProducts() throws Exception {
            String correlationId = "corr-multi-001";

            // Create reservations for multiple products
            InventoryReservation laptopRes = InventoryReservation.builder()
                    .correlationId(correlationId)
                    .productId(1)
                    .reservedQuantity(2)
                    .status(InventoryReservation.ReservationStatus.RESERVED)
                    .createdAt(LocalDateTime.now())
                    .build();
            reservationRepository.save(laptopRes);

            InventoryReservation headphonesRes = InventoryReservation.builder()
                    .correlationId(correlationId)
                    .productId(2)
                    .reservedQuantity(5)
                    .status(InventoryReservation.ReservationStatus.RESERVED)
                    .createdAt(LocalDateTime.now())
                    .build();
            reservationRepository.save(headphonesRes);

            // Publish payment failure
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    "evt-fail-002", correlationId, "ORD-MULTI-001",
                    "Payment declined", java.time.Instant.now(), 1
            );
            kafkaTemplate.send("payment.failed", correlationId, failedEvent);

            Thread.sleep(2000);

            // Verify both products are restored
            Product restoredLaptop = productRepository.findById(1).orElseThrow();
            assertThat(restoredLaptop.getAvailableQuantity()).isEqualTo(12.0);

            Product restoredHeadphones = productRepository.findById(2).orElseThrow();
            assertThat(restoredHeadphones.getAvailableQuantity()).isEqualTo(55.0);

            // Verify both reservations are released
            List<InventoryReservation> releasedReservations = reservationRepository
                    .findByCorrelationIdAndStatus(correlationId, InventoryReservation.ReservationStatus.RELEASED);
            assertThat(releasedReservations).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Inventory Compensation — Idempotency")
    class CompensationIdempotency {

        @Test
        @DisplayName("should be idempotent when no RESERVED records exist (already released)")
        void shouldBeIdempotentWhenAlreadyReleased() throws Exception {
            String correlationId = "corr-idem-001";

            // No reservations created — simulating already released state

            // Publish payment.failed
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    "evt-fail-003", correlationId, "ORD-IDEM-001",
                    "Insufficient funds", java.time.Instant.now(), 1
            );
            kafkaTemplate.send("payment.failed", correlationId, failedEvent);

            Thread.sleep(2000);

            // Should not crash — just acknowledge
            Optional<InventoryReservation> released = reservationRepository
                    .findByCorrelationIdAndStatus(correlationId, InventoryReservation.ReservationStatus.RELEASED)
                    .stream()
                    .findFirst();

            assertThat(released).isEmpty();
        }

        @Test
        @DisplayName("should not double-release if payment.failed received twice")
        void shouldNotDoubleReleaseOnDuplicateEvent() throws Exception {
            String correlationId = "corr-double-001";

            // Create one reservation
            InventoryReservation reservation = InventoryReservation.builder()
                    .correlationId(correlationId)
                    .productId(1)
                    .reservedQuantity(2)
                    .status(InventoryReservation.ReservationStatus.RESERVED)
                    .createdAt(LocalDateTime.now())
                    .build();
            reservationRepository.save(reservation);

            // Publish payment.failed twice
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    "evt-fail-004", correlationId, "ORD-DBL-001",
                    "Insufficient funds", java.time.Instant.now(), 1
            );
            kafkaTemplate.send("payment.failed", correlationId, failedEvent);
            Thread.sleep(1000);
            kafkaTemplate.send("payment.failed", correlationId, failedEvent);
            Thread.sleep(1000);

            // Stock should be restored once, not twice
            Product restoredLaptop = productRepository.findById(1).orElseThrow();
            assertThat(restoredLaptop.getAvailableQuantity()).isEqualTo(12.0);

            // Only one reservation should be RELEASED
            List<InventoryReservation> releasedReservations = reservationRepository
                    .findByCorrelationIdAndStatus(correlationId, InventoryReservation.ReservationStatus.RELEASED);
            assertThat(releasedReservations).hasSize(1);
        }
    }
}
