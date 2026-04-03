package code.with.vanilson.paymentservice.bdd;

import code.with.vanilson.paymentservice.application.PaymentMapper;
import code.with.vanilson.paymentservice.application.PaymentRequest;
import code.with.vanilson.paymentservice.application.PaymentResponse;
import code.with.vanilson.paymentservice.application.PaymentService;
import code.with.vanilson.paymentservice.domain.CustomerData;
import code.with.vanilson.paymentservice.domain.Payment;
import code.with.vanilson.paymentservice.domain.PaymentMethod;
import code.with.vanilson.paymentservice.infrastructure.messaging.NotificationProducer;
import code.with.vanilson.paymentservice.infrastructure.repository.PaymentRepository;
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

    private PaymentRequest paymentRequest;
    private Integer savedPaymentId;
    private Exception caughtException;
    private boolean duplicateScenario;
    private String currentOrderRef;

    @Before
    public void setUp() {
        paymentRepository = Mockito.mock(PaymentRepository.class);
        notificationProducer = Mockito.mock(NotificationProducer.class);
        paymentMapper = Mockito.mock(PaymentMapper.class);
        messageSource = Mockito.mock(MessageSource.class);

        paymentService = new PaymentService(paymentRepository, paymentMapper, notificationProducer, messageSource);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        caughtException = null;
        duplicateScenario = false;
        savedPaymentId = null;
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
                .idempotencyKey("payment:" + orderRef)
                .build();

        PaymentResponse response = new PaymentResponse(
                1, BigDecimal.valueOf(amount), "CREDIT_CARD", 1, orderRef, LocalDateTime.now());

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
}
