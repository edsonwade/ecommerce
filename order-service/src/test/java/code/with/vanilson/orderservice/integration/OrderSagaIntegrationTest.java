package code.with.vanilson.orderservice.integration;

import code.with.vanilson.orderservice.*;
import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerInfo;
import code.with.vanilson.orderservice.kafka.PaymentAuthorizedEvent;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.orderservice.product.ProductPurchaseRequest;
import code.with.vanilson.tenantcontext.TenantContext;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * OrderSagaIntegrationTest — Saga flow end-to-end with real PostgreSQL + EmbeddedKafka (Phase 0)
 * <p>
 * Verifies:
 * 1. Order created → status REQUESTED, outbox event persisted
 * 2. payment.authorized event → OrderSagaConsumer updates order to CONFIRMED
 * 3. payment.failed event → OrderSagaConsumer updates order to CANCELLED
 * 4. Idempotency: duplicate payment.authorized keeps status CONFIRMED (no crash)
 * <p>
 * Infrastructure:
 * - Real PostgreSQL via Testcontainers (no H2)
 * - EmbeddedKafka for Kafka message flow
 * - CustomerClient is @MockBean (external HTTP call, not under test here)
 * </p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=order-saga-group-test",
                "spring.kafka.producer.acks=all"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.authorized", "payment.failed", "inventory.insufficient", "order-topic"},
        brokerProperties = {
                "auto.create.topics.enable=true",
                // Cap internal-topic index preallocation — without these the embedded
                // broker preallocates ~2.3 GB of index files per run in %TEMP%.
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@ActiveProfiles("test")
@DisplayName("Order Saga Integration Tests — Phase 0 (Testcontainers PostgreSQL + EmbeddedKafka)")
class OrderSagaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_saga_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * The application declares its own {@code KafkaTemplate<String, String>} and
     * {@code KafkaTemplate<String, OrderConfirmation>} beans, so Boot's auto-configured
     * generic template backs off ({@code @ConditionalOnMissingBean(KafkaTemplate.class)})
     * and no {@code KafkaTemplate<String, Object>} exists to drive the saga topics from a test.
     * <p>
     * This test-only template publishes JSON, which is what {@code sagaConsumerFactory}
     * expects: it reads values with a {@code StringDeserializer} and lets
     * {@code StringJsonMessageConverter} bind the payload to the listener's event type.
     * Type headers are switched off so the converter uses the listener signature rather
     * than a serialised class name.
     */
    @TestConfiguration
    static class SagaTestProducerConfig {
        @Bean
        KafkaTemplate<String, Object> objectKafkaTemplate(KafkaProperties kafkaProperties) {
            Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @Autowired OrderService      orderService;
    @Autowired OrderRepository   orderRepository;
    @Autowired OutboxRepository  outboxRepository;
    @Autowired KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean CustomerClient customerClient;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId("test-tenant");
        when(customerClient.findCustomerById("cust-001"))
                .thenReturn(Optional.of(new CustomerInfo("cust-001", "Ana", "Silva", "ana@example.com", null)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        orderRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    // ─── Helper ────────────────────────────────────────────────────────────

    private String createTestOrder(String ref) {
        return orderService.createOrder(new OrderRequest(
                null, ref, BigDecimal.valueOf(299.99),
                PaymentMethod.CREDIT_CARD, "cust-001",
                List.of(new ProductPurchaseRequest(1, 2.0))));
    }

    private PaymentAuthorizedEvent buildPaymentAuthorizedEvent(String correlationId, String ref) {
        return new PaymentAuthorizedEvent(
                "evt-auth-" + correlationId, correlationId, ref,
                42, "cust-001", "ana@example.com", "Ana", "Silva",
                List.of(new PaymentAuthorizedEvent.ReservedItem(1, "Laptop", 2.0, BigDecimal.valueOf(1200))),
                BigDecimal.valueOf(2400), "CREDIT_CARD",
                Instant.now(), 2);
    }

    // ─── Happy path ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Order creation")
    class OrderCreation {

        @Test
        @DisplayName("should persist order with REQUESTED status and outbox event")
        void shouldCreateOrderWithRequestedStatusAndOutboxEvent() {
            String correlationId = createTestOrder("ORD-SAGA-001");

            assertThat(correlationId).isNotBlank();

            Optional<code.with.vanilson.orderservice.Order> order =
                    orderRepository.findByCorrelationId(correlationId);
            assertThat(order).isPresent();
            assertThat(order.get().getStatus()).isEqualTo(OrderStatus.REQUESTED);
            assertThat(order.get().getReference()).isEqualTo("ORD-SAGA-001");

            assertThat(outboxRepository.findPendingEvents()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Saga step 3 — payment.authorized → CONFIRMED")
    class PaymentAuthorizedFlow {

        @Test
        @DisplayName("should update order to CONFIRMED when payment.authorized received")
        void shouldConfirmOrderOnPaymentAuthorized() throws Exception {
            String correlationId = createTestOrder("ORD-SAGA-002");

            kafkaTemplate.send("payment.authorized", correlationId,
                    buildPaymentAuthorizedEvent(correlationId, "ORD-SAGA-002"));

            Thread.sleep(3000); // allow saga consumer to process

            Optional<code.with.vanilson.orderservice.Order> confirmed =
                    orderRepository.findByCorrelationId(correlationId);
            assertThat(confirmed).isPresent();
            assertThat(confirmed.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("Saga compensation — payment.failed → CANCELLED")
    class PaymentFailedFlow {

        @Test
        @DisplayName("should update order to CANCELLED when payment.failed received")
        void shouldCancelOrderOnPaymentFailed() throws Exception {
            String correlationId = createTestOrder("ORD-SAGA-003");

            kafkaTemplate.send("payment.failed", correlationId,
                    new code.with.vanilson.orderservice.kafka.PaymentFailedEvent(
                            "evt-fail-001", correlationId, "ORD-SAGA-003",
                            "Insufficient funds", Instant.now(), 1));

            Thread.sleep(3000);

            Optional<code.with.vanilson.orderservice.Order> cancelled =
                    orderRepository.findByCorrelationId(correlationId);
            assertThat(cancelled).isPresent();
            assertThat(cancelled.get().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("Saga idempotency — duplicate events")
    class SagaIdempotency {

        @Test
        @DisplayName("should remain CONFIRMED on duplicate payment.authorized (no crash)")
        void shouldBeIdempotentOnDuplicatePaymentAuthorized() throws Exception {
            String correlationId = createTestOrder("ORD-SAGA-004");
            PaymentAuthorizedEvent evt = buildPaymentAuthorizedEvent(correlationId, "ORD-SAGA-004");

            kafkaTemplate.send("payment.authorized", correlationId, evt);
            Thread.sleep(2000);
            kafkaTemplate.send("payment.authorized", correlationId, evt);
            Thread.sleep(2000);

            Optional<code.with.vanilson.orderservice.Order> order =
                    orderRepository.findByCorrelationId(correlationId);
            assertThat(order).isPresent();
            assertThat(order.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }
}
