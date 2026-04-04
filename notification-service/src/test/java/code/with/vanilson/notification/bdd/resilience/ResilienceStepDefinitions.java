package code.with.vanilson.notification.bdd.resilience;

import code.with.vanilson.notification.DlqEvent;
import code.with.vanilson.notification.DlqEventRepository;
import code.with.vanilson.notification.NotificationRepository;
import code.with.vanilson.notification.email.EmailService;
import code.with.vanilson.notification.health.KafkaHealthIndicator;
import code.with.vanilson.notification.health.SmtpHealthIndicator;
import code.with.vanilson.notification.idempotency.ProcessedEvent;
import code.with.vanilson.notification.idempotency.ProcessedEventRepository;
import code.with.vanilson.notification.kafka.DlqConsumer;
import code.with.vanilson.notification.kafka.NotificationsConsumer;
import code.with.vanilson.notification.kafka.payment.PaymentConfirmation;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.mail.MessagingException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.MessageSource;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ResilienceStepDefinitions {

    // Shared mocks
    private NotificationRepository notificationRepository;
    private EmailService emailService;
    private MessageSource messageSource;
    private ProcessedEventRepository processedEventRepository;
    private DlqEventRepository dlqEventRepository;
    private Acknowledgment acknowledgment;

    // Subjects under test
    private NotificationsConsumer consumer;
    private DlqConsumer dlqConsumer;

    // State
    private String currentOrderRef;
    private Health lastHealth;

    @Before
    public void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        emailService = mock(EmailService.class);
        messageSource = mock(MessageSource.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        dlqEventRepository = mock(DlqEventRepository.class);
        acknowledgment = mock(Acknowledgment.class);

        lenient().when(messageSource.getMessage(anyString(), any(), any(java.util.Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        consumer = new NotificationsConsumer(
                notificationRepository, emailService, messageSource, processedEventRepository);
        dlqConsumer = new DlqConsumer(dlqEventRepository);
    }

    // -------------------------------------------------------
    // Background
    // -------------------------------------------------------

    @Given("the notification service is running")
    public void theNotificationServiceIsRunning() {
        // context established by setUp()
    }

    // -------------------------------------------------------
    // Idempotency scenario
    // -------------------------------------------------------

    @Given("a payment event for order {string} has already been processed on partition {int} at offset {long}")
    public void aPaymentEventHasAlreadyBeenProcessed(String orderRef, int partition, long offset) {
        currentOrderRef = orderRef;
        when(processedEventRepository.findById("payment-topic:" + partition + ":" + offset))
                .thenReturn(Optional.of(ProcessedEvent.of("payment-topic", partition, offset)));
    }

    @When("the same payment event arrives again on partition {int} at offset {long}")
    public void theSamePaymentEventArrivesAgain(int partition, long offset) {
        PaymentConfirmation event = new PaymentConfirmation(
                currentOrderRef, BigDecimal.TEN, "CREDIT_CARD",
                "Jane", "Doe", "jane@example.com");
        consumer.consumePaymentSuccessNotifications(event, partition, offset, acknowledgment);
    }

    @Then("no email is sent")
    public void noEmailIsSent() {
        verify(emailService, never()).sendPaymentSuccessEmail(any(), any(), any(), any());
        verify(emailService, never()).sendOrderConfirmationEmail(any(), any(), any(), any(), any());
    }

    @And("the Kafka offset is acknowledged to avoid redelivery")
    public void theKafkaOffsetIsAcknowledged() {
        verify(acknowledgment).acknowledge();
    }

    // -------------------------------------------------------
    // DLQ scenarios
    // -------------------------------------------------------

    @When("a payment DLQ event for order {string} arrives on topic {string} partition {int} at offset {long}")
    public void aPaymentDlqEventArrives(String orderRef, String topic, int partition, long offset) {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                topic, partition, offset, "key",
                "{\"orderReference\":\"" + orderRef + "\"}");
        dlqConsumer.consumePaymentDlq(record);
    }

    @When("an order DLQ event for order {string} arrives on topic {string} partition {int} at offset {long}")
    public void anOrderDlqEventArrives(String orderRef, String topic, int partition, long offset) {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                topic, partition, offset, "key",
                "{\"orderReference\":\"" + orderRef + "\"}");
        dlqConsumer.consumeOrderDlq(record);
    }

    @Then("the DLQ event is saved to MongoDB with topic {string}")
    public void theDlqEventIsSavedWithTopic(String expectedTopic) {
        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo(expectedTopic);
    }

    // -------------------------------------------------------
    // Health indicator scenarios
    // -------------------------------------------------------

    @Given("the Kafka broker is unreachable")
    public void theKafkaBrokerIsUnreachable() {
        // signal used in @When step
    }

    @When("the Kafka health indicator is checked")
    public void theKafkaHealthIndicatorIsChecked() throws Exception {
        AdminClient adminClient = mock(AdminClient.class);
        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Set<String>> future = mock(KafkaFuture.class);

        when(adminClient.listTopics()).thenReturn(listTopicsResult);
        when(listTopicsResult.names()).thenReturn(future);
        when(future.get(3, TimeUnit.SECONDS))
                .thenThrow(new ExecutionException("Connection refused", new RuntimeException()));

        lastHealth = new KafkaHealthIndicator(adminClient).health();
    }

    @Given("the SMTP server is unreachable")
    public void theSmtpServerIsUnreachable() {
        // signal used in @When step
    }

    @When("the SMTP health indicator is checked")
    public void theSmtpHealthIndicatorIsChecked() throws Exception {
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(sender.getHost()).thenReturn("smtp.gmail.com");
        when(sender.getPort()).thenReturn(587);
        doThrow(new MessagingException("Connection refused")).when(sender).testConnection();

        lastHealth = new SmtpHealthIndicator(sender).health();
    }

    @Then("the health status is {string}")
    public void theHealthStatusIs(String expectedStatus) {
        assertThat(lastHealth.getStatus()).isEqualTo(new Status(expectedStatus));
    }

    @And("the health details contain an {string} field")
    public void theHealthDetailsContainField(String field) {
        assertThat(lastHealth.getDetails()).containsKey(field);
    }
}
