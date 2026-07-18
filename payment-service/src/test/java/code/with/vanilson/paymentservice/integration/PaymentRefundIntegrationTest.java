package code.with.vanilson.paymentservice.integration;

import code.with.vanilson.paymentservice.application.PaymentResponse;
import code.with.vanilson.paymentservice.application.PaymentService;
import code.with.vanilson.paymentservice.domain.Payment;
import code.with.vanilson.paymentservice.domain.PaymentMethod;
import code.with.vanilson.paymentservice.domain.PaymentStatus;
import code.with.vanilson.paymentservice.exception.PaymentConflictException;
import code.with.vanilson.paymentservice.exception.PaymentNotFoundException;
import code.with.vanilson.paymentservice.infrastructure.outbox.PaymentOutboxEvent;
import code.with.vanilson.paymentservice.infrastructure.outbox.PaymentOutboxRepository;
import code.with.vanilson.paymentservice.infrastructure.repository.PaymentRepository;
import code.with.vanilson.tenantcontext.TenantContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PaymentRefundIntegrationTest — Fase 6 + 6.1 against real PostgreSQL + EmbeddedKafka.
 * <p>
 * Fase 6: a refund flips the persisted payment AUTHORIZED → REFUNDED, and a second
 * attempt is rejected with 409 without re-mutating the row.
 * <p>
 * Fase 6.1 (outbox): the refund no longer sends to Kafka on the request thread — it
 * writes a {@code payment.refunded} row to the transactional outbox in the same TX, and
 * {@code PaymentOutboxPublisher} (scheduled) drains it to the broker. Proven here by
 * asserting the outbox row exists (status-independent — the scheduler may already have
 * flipped it PENDING→PUBLISHED) AND that a real consumer receives the event.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=payment-refund-integration-test",
                "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==",
                // Drain fast so the publish assertion doesn't wait the default 5s.
                "payment.outbox.poll-interval-ms=500"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"inventory.reserved", "payment.authorized", "payment.failed", "payment-topic", "payment.refunded"},
        brokerProperties = {
                "auto.create.topics.enable=true",
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@DisplayName("Payment Refund Integration — Fase 6 + 6.1 (Testcontainers PostgreSQL + EmbeddedKafka)")
class PaymentRefundIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_refund_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String TENANT = "test-tenant";

    @Autowired PaymentService         paymentService;
    @Autowired PaymentRepository      paymentRepository;
    @Autowired PaymentOutboxRepository outboxRepository;
    @Autowired EmbeddedKafkaBroker    embeddedKafka;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
        TenantContext.clear();
    }

    private Integer persistAuthorizedPayment(String orderReference) {
        Payment payment = Payment.builder()
                .amount(BigDecimal.valueOf(150.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .orderId(77)
                .orderReference(orderReference)
                .idempotencyKey("payment:" + orderReference)
                .tenantId(TENANT)
                .build(); // status defaults to AUTHORIZED via @Builder.Default
        return paymentRepository.save(payment).getPaymentId();
    }

    @Test
    @DisplayName("refunding an AUTHORIZED payment flips the row to REFUNDED and writes ONE outbox row")
    void refundFlipsStatusAndWritesOutbox() {
        Integer paymentId = persistAuthorizedPayment("ORD-REFUND-001");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.AUTHORIZED);

        PaymentResponse response = paymentService.refundPayment(paymentId);

        assertThat(response.status()).isEqualTo("REFUNDED");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);

        // findAll() — status-independent — because the scheduler may have already
        // flipped the row PENDING→PUBLISHED by the time we assert (lesson from
        // OrderRefundIntegrationTest).
        long refundRows = outboxRepository.findAll().stream()
                .filter(o -> o.getTopic().equals("payment.refunded")
                        && o.getCorrelationId().equals("ORD-REFUND-001"))
                .count();
        assertThat(refundRows)
                .as("exactly one payment.refunded outbox row for this order")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the outbox publisher drains the refund event to the broker (real consumer receives it)")
    void publisherDeliversRefundEventToBroker() {
        try (Consumer<String, String> consumer = newConsumer("payment.refunded")) {
            Integer paymentId = persistAuthorizedPayment("ORD-REFUND-PUB");

            paymentService.refundPayment(paymentId);

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "payment.refunded", Duration.ofSeconds(15));

            assertThat(record.key())
                    .as("partition key is the order reference")
                    .isEqualTo("ORD-REFUND-PUB");
            assertThat(record.value())
                    .as("raw JSON event carries the order reference and payment id")
                    .contains("ORD-REFUND-PUB", "\"paymentId\":" + paymentId);

            // And the source row is now marked PUBLISHED.
            boolean published = outboxRepository.findAll().stream()
                    .anyMatch(o -> o.getCorrelationId().equals("ORD-REFUND-PUB")
                            && o.getStatus() == PaymentOutboxEvent.OutboxStatus.PUBLISHED);
            assertThat(published).as("outbox row flipped to PUBLISHED after delivery").isTrue();
        }
    }

    @Test
    @DisplayName("a second refund attempt is rejected with 409 and writes no extra outbox row")
    void secondRefundIsRejected() {
        Integer paymentId = persistAuthorizedPayment("ORD-REFUND-002");
        paymentService.refundPayment(paymentId);

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId))
                .isInstanceOf(PaymentConflictException.class);

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        long refundRows = outboxRepository.findAll().stream()
                .filter(o -> o.getCorrelationId().equals("ORD-REFUND-002"))
                .count();
        assertThat(refundRows).as("only the first refund wrote an outbox row").isEqualTo(1);
    }

    @Test
    @DisplayName("refunding a non-existent payment throws 404 and writes no outbox row")
    void refundingMissingPaymentThrows404() {
        assertThatThrownBy(() -> paymentService.refundPayment(999_999))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThat(outboxRepository.count()).isZero();
    }

    private Consumer<String, String> newConsumer(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "payment-refund-outbox-verify", "true", embeddedKafka);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }
}
