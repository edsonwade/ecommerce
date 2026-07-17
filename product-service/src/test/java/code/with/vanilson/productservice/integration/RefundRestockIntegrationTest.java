package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.kafka.OrderRefundedEvent;
import code.with.vanilson.productservice.kafka.PaymentFailedEvent;
import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import org.junit.jupiter.api.*;
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
 * RefundRestockIntegrationTest — Fase 6 (basic refunds), clone of
 * {@link InventoryCompensationIntegrationTest}'s structure for the {@code order.refunded}
 * trigger. Also proves the pre-existing compensation path ({@code payment.failed} →
 * {@code InventoryCompensationConsumer}) is untouched — both consumers share
 * {@code inventoryKafkaListenerContainerFactory}, so a wiring mistake in one could silently
 * break the other.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=test-refund-restock",
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
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@ActiveProfiles("test")
@DisplayName("Refund Restock Integration Tests (Fase 6)")
class RefundRestockIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("product_refund_restock_test")
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
        productRepository.deleteAll();
        reservationRepository.deleteAll();

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
        productRepository.deleteAll();
        reservationRepository.deleteAll();
    }

    @Test
    @DisplayName("order.refunded restores stock and marks the reservation RELEASED")
    void shouldRestockOnOrderRefunded() throws Exception {
        String correlationId = "corr-refund-int-001";
        reservationRepository.save(InventoryReservation.builder()
                .correlationId(correlationId)
                .productId(laptop.getId())
                .reservedQuantity(3)
                .status(InventoryReservation.ReservationStatus.RESERVED)
                .createdAt(LocalDateTime.now())
                .build());

        OrderRefundedEvent event = new OrderRefundedEvent(
                "evt-refund-int-001", correlationId, "ORD-REFUND-INT-001", Instant.now(), 1);
        kafkaTemplate.send("order.refunded", correlationId, event);

        Thread.sleep(2000);

        Product restored = productRepository.findById(laptop.getId()).orElseThrow();
        assertThat(restored.getAvailableQuantity()).isEqualTo(13.0);

        List<InventoryReservation> released = reservationRepository
                .findByCorrelationIdAndStatus(correlationId, InventoryReservation.ReservationStatus.RELEASED);
        assertThat(released).hasSize(1);
        assertThat(released.get(0).getReleasedAt()).isNotNull();
    }

    @Test
    @DisplayName("redelivering order.refunded is idempotent — stock restored once")
    void shouldNotDoubleRestockOnDuplicateEvent() throws Exception {
        String correlationId = "corr-refund-int-002";
        reservationRepository.save(InventoryReservation.builder()
                .correlationId(correlationId)
                .productId(laptop.getId())
                .reservedQuantity(2)
                .status(InventoryReservation.ReservationStatus.RESERVED)
                .createdAt(LocalDateTime.now())
                .build());

        OrderRefundedEvent event = new OrderRefundedEvent(
                "evt-refund-int-002", correlationId, "ORD-REFUND-INT-002", Instant.now(), 1);
        kafkaTemplate.send("order.refunded", correlationId, event);
        Thread.sleep(1500);
        kafkaTemplate.send("order.refunded", correlationId, event);
        Thread.sleep(1500);

        Product restored = productRepository.findById(laptop.getId()).orElseThrow();
        assertThat(restored.getAvailableQuantity()).isEqualTo(12.0);

        List<InventoryReservation> released = reservationRepository
                .findByCorrelationIdAndStatus(correlationId, InventoryReservation.ReservationStatus.RELEASED);
        assertThat(released).hasSize(1);
    }

    @Test
    @DisplayName("regression: the payment.failed compensation path is unaffected by the new listener")
    void compensationPathStillWorks() throws Exception {
        String correlationId = "corr-comp-regression-001";
        reservationRepository.save(InventoryReservation.builder()
                .correlationId(correlationId)
                .productId(laptop.getId())
                .reservedQuantity(4)
                .status(InventoryReservation.ReservationStatus.RESERVED)
                .createdAt(LocalDateTime.now())
                .build());

        PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                "evt-fail-regression-001", correlationId, "ORD-COMP-REGRESSION-001",
                "Insufficient funds", Instant.now(), 1);
        kafkaTemplate.send("payment.failed", correlationId, failedEvent);

        Thread.sleep(2000);

        Product restored = productRepository.findById(laptop.getId()).orElseThrow();
        assertThat(restored.getAvailableQuantity()).isEqualTo(14.0);

        List<InventoryReservation> released = reservationRepository
                .findByCorrelationIdAndStatus(correlationId, InventoryReservation.ReservationStatus.RELEASED);
        assertThat(released).hasSize(1);
    }
}
