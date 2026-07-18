package code.with.vanilson.paymentservice.bdd;

import code.with.vanilson.paymentservice.application.PaymentMapper;
import code.with.vanilson.paymentservice.application.PaymentRequest;
import code.with.vanilson.paymentservice.application.PaymentResponse;
import code.with.vanilson.paymentservice.application.PaymentService;
import code.with.vanilson.paymentservice.domain.CustomerData;
import code.with.vanilson.paymentservice.domain.Payment;
import code.with.vanilson.paymentservice.domain.PaymentMethod;
import code.with.vanilson.paymentservice.domain.PaymentStatus;
import code.with.vanilson.paymentservice.exception.PaymentConflictException;
import code.with.vanilson.paymentservice.infrastructure.messaging.NotificationProducer;
import code.with.vanilson.paymentservice.infrastructure.outbox.PaymentOutboxEvent;
import code.with.vanilson.paymentservice.infrastructure.outbox.PaymentOutboxRepository;
import code.with.vanilson.paymentservice.infrastructure.repository.PaymentRepository;
import code.with.vanilson.tenantcontext.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PaymentStepDefinitions {

    private PaymentService paymentService;
    private PaymentRepository paymentRepository;
    private NotificationProducer notificationProducer;
    private PaymentMapper paymentMapper;
    private MessageSource messageSource;
    private PaymentOutboxRepository outboxRepository;
    private ObjectMapper objectMapper;

    private PaymentRequest paymentRequest;
    private Integer savedPaymentId;
    private Exception caughtException;
    private boolean duplicateScenario;
    private String currentOrderRef;

    // Refund scenario state (Fase 6)
    private static final Integer REFUND_PAYMENT_ID = 42;
    private Payment refundTargetPayment;
    private code.with.vanilson.paymentservice.application.PaymentResponse refundResult;

    @Before
    public void setUp() {
        paymentRepository = Mockito.mock(PaymentRepository.class);
        notificationProducer = Mockito.mock(NotificationProducer.class);
        paymentMapper = Mockito.mock(PaymentMapper.class);
        messageSource = Mockito.mock(MessageSource.class);
        outboxRepository = Mockito.mock(PaymentOutboxRepository.class);
        objectMapper = new ObjectMapper().findAndRegisterModules(); // real — serialises the event

        paymentService = new PaymentService(paymentRepository, paymentMapper, notificationProducer,
                messageSource, outboxRepository, objectMapper);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Saga consumer / gateway filter seeds the tenant; PaymentService stamps payment.tenant_id from it.
        TenantContext.setCurrentTenantId("tenant-test-001");

        caughtException = null;
        duplicateScenario = false;
        savedPaymentId = null;
    }

    @After
    public void tearDown() {
        TenantContext.clear();
    }

    @Given("a valid payment request for order {string} with amount {double}")
    public void a_valid_payment_request(String orderRef, double amount) {
        this.currentOrderRef = orderRef;
        CustomerData cust = new CustomerData("c-01", "Ana", "Silva", "ana@example.com");
        paymentRequest = new PaymentRequest(null, BigDecimal.valueOf(amount), PaymentMethod.CREDIT_CARD,
                1, orderRef, cust);

        Payment payment = Payment.builder()
                .paymentId(1)
                .orderReference(orderRef)
                .amount(BigDecimal.valueOf(amount))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .idempotencyKey("payment:" + orderRef)
                .build();

        PaymentResponse response = new PaymentResponse(
                1, BigDecimal.valueOf(amount), "CREDIT_CARD", 1, orderRef, LocalDateTime.now(), "AUTHORIZED");

        lenient().when(paymentMapper.toPayment(any())).thenReturn(payment);
        lenient().when(paymentMapper.toResponse(any())).thenReturn(response);
    }

    @Given("the payment has not been processed yet")
    public void payment_has_not_been_processed() {
        when(paymentRepository.findByIdempotencyKey("payment:" + currentOrderRef))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getPaymentId() == null) {
                p.setPaymentId(1);
            }
            return p;
        });
        duplicateScenario = false;
    }

    @Given("the payment has already been processed successfully")
    public void payment_has_already_been_processed() {
        Payment existingPayment = Payment.builder()
                .paymentId(1)
                .orderReference(currentOrderRef)
                .idempotencyKey("payment:" + currentOrderRef)
                .build();
        when(paymentRepository.findByIdempotencyKey("payment:" + currentOrderRef))
                .thenReturn(Optional.of(existingPayment));
        duplicateScenario = true;
    }

    @When("the payment is submitted")
    public void the_payment_is_submitted() {
        try {
            savedPaymentId = paymentService.createPayment(paymentRequest);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("the payment should be recorded with status {string}")
    public void the_payment_should_be_recorded(String status) {
        assertThat(caughtException).isNull();
        if (!duplicateScenario) {
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    @Then("a notification should be sent for order {string}")
    public void a_notification_should_be_sent(String orderRef) {
        if (!duplicateScenario) {
            verify(notificationProducer).sendNotification(any());
        } else {
            verify(notificationProducer, never()).sendNotification(any());
        }
    }

    @Then("the system should reject the duplicate payment request")
    public void the_system_should_reject_duplicate() {
        // In the new idempotent implementation, it returns the existing ID instead of throwing an error
        assertThat(caughtException).isNull();
        assertThat(savedPaymentId).isEqualTo(1);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // -------------------------------------------------------
    // Refund (Fase 6)
    // -------------------------------------------------------

    @Given("an authorized payment exists for order {string}")
    public void an_authorized_payment_exists(String orderRef) {
        refundTargetPayment = Payment.builder()
                .paymentId(REFUND_PAYMENT_ID)
                .orderId(7)
                .orderReference(orderRef)
                .amount(BigDecimal.valueOf(75.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.AUTHORIZED)
                .tenantId("tenant-test-001")
                .build();
        when(paymentRepository.findById(REFUND_PAYMENT_ID)).thenReturn(Optional.of(refundTargetPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            return new code.with.vanilson.paymentservice.application.PaymentResponse(
                    p.getPaymentId(), p.getAmount(), p.getPaymentMethod().name(),
                    p.getOrderId(), p.getOrderReference(), LocalDateTime.now(), p.getStatus().name());
        });
    }

    @Given("an already-refunded payment exists for order {string}")
    public void an_already_refunded_payment_exists(String orderRef) {
        refundTargetPayment = Payment.builder()
                .paymentId(REFUND_PAYMENT_ID)
                .orderId(7)
                .orderReference(orderRef)
                .amount(BigDecimal.valueOf(75.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.REFUNDED)
                .build();
        when(paymentRepository.findById(REFUND_PAYMENT_ID)).thenReturn(Optional.of(refundTargetPayment));
    }

    @When("the payment is refunded")
    public void the_payment_is_refunded() {
        try {
            refundResult = paymentService.refundPayment(REFUND_PAYMENT_ID);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("the payment status becomes {string}")
    public void the_payment_status_becomes(String status) {
        assertThat(caughtException).isNull();
        assertThat(refundResult).isNotNull();
        assertThat(refundResult.status()).isEqualTo(status);
    }

    @Then("a payment.refunded event is published")
    public void a_payment_refunded_event_is_published() {
        // Fase 6.1: no direct Kafka send — the refund writes a PENDING payment.refunded
        // row to the outbox (the scheduled publisher delivers it off the request thread).
        ArgumentCaptor<PaymentOutboxEvent> captor = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("payment.refunded");
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentOutboxEvent.OutboxStatus.PENDING);
    }

    @Then("the refund is rejected as already processed")
    public void the_refund_is_rejected_as_already_processed() {
        assertThat(caughtException).isInstanceOf(PaymentConflictException.class);
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
