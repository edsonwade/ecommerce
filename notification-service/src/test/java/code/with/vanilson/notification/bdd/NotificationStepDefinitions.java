package code.with.vanilson.notification.bdd;

import code.with.vanilson.notification.Notification;
import code.with.vanilson.notification.NotificationRepository;
import code.with.vanilson.notification.NotificationType;
import code.with.vanilson.notification.email.EmailService;
import code.with.vanilson.notification.kafka.NotificationsConsumer;
import code.with.vanilson.notification.kafka.order.OrderConfirmation;
import code.with.vanilson.notification.kafka.payment.PaymentConfirmation;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class NotificationStepDefinitions {

    private NotificationRepository repository;
    private EmailService emailService;
    private MessageSource messageSource;
    private Acknowledgment acknowledgment;
    private NotificationsConsumer consumer;

    private PaymentConfirmation paymentMessage;
    private OrderConfirmation orderMessage;

    @Before
    public void setUp() {
        repository = Mockito.mock(NotificationRepository.class);
        emailService = Mockito.mock(EmailService.class);
        messageSource = Mockito.mock(MessageSource.class);
        acknowledgment = Mockito.mock(Acknowledgment.class);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        consumer = new NotificationsConsumer(repository, emailService, messageSource);
    }

    @Given("a payment confirmation message for order {string} with amount {double}")
    public void a_payment_confirmation_message(String ref, double amount) {
        paymentMessage = new PaymentConfirmation(ref, BigDecimal.valueOf(amount), "CREDIT_CARD",
                "Ana", "Silva", "ana@example.com");
    }

    @When("the payment notification is consumed from Kafka")
    public void the_payment_notification_is_consumed() {
        consumer.consumePaymentSuccessNotifications(paymentMessage, 0, 100L, acknowledgment);
    }

    @Then("a PAYMENT_CONFIRMATION notification is saved to the database")
    public void a_payment_confirmation_notification_is_saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.PAYMENT_CONFIRMATION);
    }

    @Then("an email is sent to the customer for the payment")
    public void an_email_is_sent_for_payment() {
        verify(emailService).sendPaymentSuccessEmail(
                eq("ana@example.com"), eq("Ana Silva"), eq(paymentMessage.amount()), eq(paymentMessage.orderReference()));
    }

    @Given("an order confirmation message for order {string} with amount {double}")
    public void an_order_confirmation_message(String ref, double amount) {
        OrderConfirmation.CustomerSummary customer = new OrderConfirmation.CustomerSummary(
                "c-1", "Ana", "Silva", "ana@example.com");
        OrderConfirmation.ProductSummary product = new OrderConfirmation.ProductSummary(
                1, "Prod", "Desc", BigDecimal.valueOf(amount), 1.0);
        orderMessage = new OrderConfirmation(ref, BigDecimal.valueOf(amount), "CREDIT_CARD",
                customer, List.of(product));
    }

    @When("the order notification is consumed from Kafka")
    public void the_order_notification_is_consumed() {
        consumer.consumeOrderConfirmationNotifications(orderMessage, 0, 200L, acknowledgment);
    }

    @Then("an ORDER_CONFIRMATION notification is saved to the database")
    public void an_order_confirmation_notification_is_saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ORDER_CONFIRMATION);
    }

    @Then("an email with a PDF invoice is sent to the customer")
    public void an_email_with_pdf_is_sent_for_order() {
        verify(emailService).sendOrderConfirmationEmail(
                eq("ana@example.com"), eq("Ana Silva"), eq(orderMessage.totalAmount()),
                eq(orderMessage.orderReference()), eq(orderMessage));
    }

    @Then("the Kafka message is acknowledged")
    public void the_kafka_message_is_acknowledged() {
        verify(acknowledgment).acknowledge();
    }
}
