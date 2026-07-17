package code.with.vanilson.paymentservice.integration;

import code.with.vanilson.paymentservice.application.PaymentResponse;
import code.with.vanilson.paymentservice.application.PaymentService;
import code.with.vanilson.paymentservice.domain.Payment;
import code.with.vanilson.paymentservice.domain.PaymentMethod;
import code.with.vanilson.paymentservice.domain.PaymentStatus;
import code.with.vanilson.paymentservice.exception.PaymentConflictException;
import code.with.vanilson.paymentservice.exception.PaymentNotFoundException;
import code.with.vanilson.paymentservice.infrastructure.repository.PaymentRepository;
import code.with.vanilson.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PaymentRefundIntegrationTest — Fase 6 (basic refunds) against real PostgreSQL.
 * <p>
 * Proves the {@code V1.4} migration end-to-end: a payment persisted before the refund
 * carries {@code status=AUTHORIZED} (the column default), a refund flips it to
 * {@code REFUNDED} in the real row, and a second refund attempt is rejected with 409
 * without mutating the already-refunded row.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=payment-refund-integration-test",
                "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA=="
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
@DisplayName("Payment Refund Integration — Fase 6 (Testcontainers PostgreSQL + EmbeddedKafka)")
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

    @Autowired PaymentService    paymentService;
    @Autowired PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
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
    @DisplayName("refunding an AUTHORIZED payment flips status to REFUNDED in the real row")
    void refundFlipsStatusInDatabase() {
        Integer paymentId = persistAuthorizedPayment("ORD-REFUND-001");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.AUTHORIZED);

        PaymentResponse response = paymentService.refundPayment(paymentId);

        assertThat(response.status()).isEqualTo("REFUNDED");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("a second refund attempt is rejected with 409 and does not re-mutate the row")
    void secondRefundIsRejected() {
        Integer paymentId = persistAuthorizedPayment("ORD-REFUND-002");
        paymentService.refundPayment(paymentId);

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId))
                .isInstanceOf(PaymentConflictException.class);

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("refunding a non-existent payment throws 404")
    void refundingMissingPaymentThrows404() {
        assertThatThrownBy(() -> paymentService.refundPayment(999_999))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
