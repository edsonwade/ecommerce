package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import code.with.vanilson.productservice.kafka.OrderRequestedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InventoryReservationIdempotencyIntegrationTest — B4 (idempotency as a platform convention)
 * <p>
 * Proves that a redelivered order.requested event (Kafka at-least-once) does NOT
 * deduct stock a second time: the InventoryReservation rows committed in the same
 * transaction as the deduction act as the processed-event record, and the consumer
 * skips the reservation core when rows for the correlationId already exist.
 * <p>
 * Recipe (same as {@link InventoryCompensationIntegrationTest}):
 * - Real PostgreSQL via Testcontainers (never H2) — the {@code test} profile declares
 *   the PostgreSQL driver but no JDBC url, so the datasource comes from the container.
 * - EmbeddedKafka for the real listener path (broker index preallocation capped).
 * - Redis mocked: the L2 cache is not what this test proves, and on this host the
 *   Lettuce client hangs inside the test JVM.
 * - Products seeded without category: the consumer flow never touches ProductMapper,
 *   which is the only place that requires a category.
 * </p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=test-inventory-idempotency",
                "application.security.jwt.secret-key=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {
                "auto.create.topics.enable=true",
                "log.retention.hours=1",
                // Cap internal-topic index preallocation — without these the embedded
                // broker preallocates ~2.3 GB of index files per run in %TEMP%.
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@ActiveProfiles("test")
@DisplayName("Inventory Reservation Idempotency Integration Tests (B4)")
class InventoryReservationIdempotencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("product_inventory_idempotency_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockBean StringRedisTemplate stringRedisTemplate;
    @MockBean RedisConnectionFactory redisConnectionFactory;

    @Autowired ProductRepository productRepository;
    @Autowired InventoryReservationRepository reservationRepository;
    @Autowired KafkaTemplate<String, Object> kafkaTemplate;

    private Product laptop;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        productRepository.deleteAll();

        // Ids are sequence-generated: assigning them by hand makes Spring Data treat the
        // entity as detached and merge it under a *different* id, so every findById(1) misses.
        // tenant_id / created_by are NOT NULL since the Phase 4 tenant + RBAC migrations.
        laptop = productRepository.save(Product.builder()
                .name("Laptop")
                .description("Gaming Laptop")
                .availableQuantity(10.0)
                .price(BigDecimal.valueOf(1200))
                .tenantId("default")
                .createdBy("system")
                .build());
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        productRepository.deleteAll();
    }

    private OrderRequestedEvent orderRequested(String correlationId, double quantity) {
        return new OrderRequestedEvent(
                "evt-" + correlationId,
                correlationId,
                "cust-001",
                "ana@example.com",
                "Ana",
                "Silva",
                List.of(new OrderRequestedEvent.ProductPurchaseItem(laptop.getId(), quantity)),
                BigDecimal.valueOf(3600),
                "CREDIT_CARD",
                "ORD-" + correlationId,
                "default",
                42,
                Instant.now(),
                1
        );
    }

    @Nested
    @DisplayName("Duplicate order.requested delivery")
    class DuplicateDelivery {

        @Test
        @DisplayName("should deduct stock exactly once when the same event is delivered twice")
        void shouldDeductStockOnceOnDuplicateDelivery() throws Exception {
            String correlationId = "corr-idem-res-001";
            OrderRequestedEvent event = orderRequested(correlationId, 3.0);

            kafkaTemplate.send("order.requested", correlationId, event);
            Thread.sleep(2000);
            kafkaTemplate.send("order.requested", correlationId, event);
            Thread.sleep(2000);

            // Stock deducted once: 10 - 3 = 7, NOT 4
            Product after = productRepository.findById(laptop.getId()).orElseThrow();
            assertThat(after.getAvailableQuantity())
                    .as("A redelivered order.requested must not deduct stock a second time")
                    .isEqualTo(7.0);

            // Exactly one reservation row — the duplicate must not add another
            List<InventoryReservation> rows = reservationRepository.findByCorrelationId(correlationId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RESERVED);
            assertThat(rows.get(0).getReservedQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("should not re-reserve when the saga was already compensated (rows RELEASED)")
        void shouldNotReReserveAfterCompensation() throws Exception {
            String correlationId = "corr-idem-res-002";

            // Simulate a saga that reserved and was then compensated: the row is
            // RELEASED and the stock is back at its full level.
            reservationRepository.save(InventoryReservation.builder()
                    .correlationId(correlationId)
                    .productId(laptop.getId())
                    .reservedQuantity(3)
                    .status(InventoryReservation.ReservationStatus.RELEASED)
                    .createdAt(LocalDateTime.now())
                    .releasedAt(LocalDateTime.now())
                    .build());

            kafkaTemplate.send("order.requested", correlationId, orderRequested(correlationId, 3.0));
            Thread.sleep(2000);

            // Stock untouched — the dead saga must not reserve again
            Product after = productRepository.findById(laptop.getId()).orElseThrow();
            assertThat(after.getAvailableQuantity()).isEqualTo(10.0);

            // Still exactly one row, still RELEASED
            List<InventoryReservation> rows = reservationRepository.findByCorrelationId(correlationId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RELEASED);
        }
    }
}
