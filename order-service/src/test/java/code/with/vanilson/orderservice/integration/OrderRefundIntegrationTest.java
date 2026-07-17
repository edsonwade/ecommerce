package code.with.vanilson.orderservice.integration;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.kafka.PaymentRefundedEvent;
import code.with.vanilson.orderservice.outbox.OutboxEvent;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantContext;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderRefundIntegrationTest — Fase 6 refunds, real PostgreSQL + a real Kafka message
 * through the actual {@code payment.refunded} listener (not a direct service call), so a
 * field-name mismatch between payment-service's producer event and this consumer's POJO —
 * exactly the class of bug {@code StringJsonMessageConverter} binding can hide — would
 * surface here.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=order-refund-integration-test",
                "spring.kafka.producer.acks=all"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.refunded", "order.refunded"},
        brokerProperties = {
                "auto.create.topics.enable=true",
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@ActiveProfiles("test")
@DisplayName("Order Refund Integration — Fase 6 (Testcontainers PostgreSQL + EmbeddedKafka)")
class OrderRefundIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_refund_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * Same test-only JSON producer template as {@code OrderSagaIntegrationTest} — the
     * consumer factory reads values as plain strings and lets
     * {@code StringJsonMessageConverter} bind by the listener's declared payload type, not
     * a serialised class-name header.
     */
    @TestConfiguration
    static class RefundTestProducerConfig {
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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private Integer persistOrder(OrderStatus status) {
        Order order = Order.builder()
                .tenantId("test-tenant")
                .correlationId(UUID.randomUUID().toString())
                .reference("ORD-" + UUID.randomUUID().toString().substring(0, 8))
                .totalAmount(BigDecimal.valueOf(150.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId("cust-refund")
                .status(status)
                .build();
        return orderRepository.save(order).getOrderId();
    }

    @Test
    @DisplayName("a real payment.refunded message flips the order to REFUNDED and writes the outbox row")
    void paymentRefundedMessageAppliesRefund() throws Exception {
        Integer orderId = persistOrder(OrderStatus.CONFIRMED);

        PaymentRefundedEvent event = new PaymentRefundedEvent(
                "evt-int-refund-1", 1001, orderId, "ORD-INT", BigDecimal.valueOf(150.00), Instant.now(), 1);
        kafkaTemplate.send("payment.refunded", String.valueOf(orderId), event);

        Thread.sleep(3000); // allow PaymentRefundConsumer to process

        Order refunded = orderRepository.findById(orderId).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED);

        // findAll() — not findPendingEvents() — because OutboxEventPublisher (fixedDelay=5s)
        // may have already marked the row PUBLISHED within the 3-second sleep window.
        List<OutboxEvent> outboxRows = outboxRepository.findAll();
        assertThat(outboxRows)
                .as("order.refunded outbox row must exist (PENDING or PUBLISHED)")
                .anyMatch(o -> o.getTopic().equals("order.refunded")
                        && o.getCorrelationId().equals(refunded.getCorrelationId()));
    }

    @Test
    @DisplayName("redelivering the same message is idempotent — status stays REFUNDED, no second outbox row")
    void redeliveredMessageIsIdempotent() throws Exception {
        Integer orderId = persistOrder(OrderStatus.CONFIRMED);
        String correlationId = orderRepository.findById(orderId).orElseThrow().getCorrelationId();

        PaymentRefundedEvent event = new PaymentRefundedEvent(
                "evt-int-refund-2", 1002, orderId, "ORD-INT-2", BigDecimal.valueOf(150.00), Instant.now(), 1);
        kafkaTemplate.send("payment.refunded", String.valueOf(orderId), event);
        Thread.sleep(3000);

        // Redeliver the identical event (at-least-once semantics)
        kafkaTemplate.send("payment.refunded", String.valueOf(orderId), event);
        Thread.sleep(3000);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);

        // findAll() — not findPendingEvents() — because OutboxEventPublisher (fixedDelay=5s)
        // may have already marked the row PUBLISHED within the total 6-second sleep window.
        long refundRowsForOrder = outboxRepository.findAll().stream()
                .filter(o -> o.getTopic().equals("order.refunded") && o.getCorrelationId().equals(correlationId))
                .count();
        assertThat(refundRowsForOrder)
                .as("second delivery must be a no-op — exactly one outbox row for this order")
                .isEqualTo(1);
    }
}
