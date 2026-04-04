package code.with.vanilson.notification.kafka;

import code.with.vanilson.notification.Notification;
import code.with.vanilson.notification.NotificationRepository;
import code.with.vanilson.notification.NotificationType;
import code.with.vanilson.notification.email.EmailService;
import code.with.vanilson.notification.idempotency.ProcessedEventRepository;
import code.with.vanilson.notification.kafka.order.OrderConfirmation;
import code.with.vanilson.notification.kafka.payment.PaymentConfirmation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationsConsumer — Unit Tests")
class NotificationsConsumerTest {

    @Mock private NotificationRepository repository;
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private Acknowledgment acknowledgment;

    @InjectMocks
    private NotificationsConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("should consume payment confirmation and send email")
    void shouldConsumePaymentConfirmation() {
        PaymentConfirmation payment = new PaymentConfirmation(
                "REF-PAY", BigDecimal.valueOf(100), "CREDIT_CARD", "Ana", "Silva", "ana@example.com");

        consumer.consumePaymentSuccessNotifications(payment, 0, 100L, acknowledgment);

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().getType()).isEqualTo(NotificationType.PAYMENT_CONFIRMATION);
        assertThat(notifCaptor.getValue().getPaymentConfirmation()).isEqualTo(payment);

        verify(emailService).sendPaymentSuccessEmail(
                "ana@example.com", "Ana Silva", BigDecimal.valueOf(100), "REF-PAY");

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("should consume order confirmation and send email")
    void shouldConsumeOrderConfirmation() {
        OrderConfirmation.CustomerSummary customer = new OrderConfirmation.CustomerSummary(
                "c-1", "Ana", "Silva", "ana@example.com");
        OrderConfirmation.ProductSummary product = new OrderConfirmation.ProductSummary(
                1, "Laptop", "Gaming PC", BigDecimal.valueOf(1000), 1.0);
        OrderConfirmation order = new OrderConfirmation(
                "REF-ORD", BigDecimal.valueOf(1000), "CREDIT_CARD", customer, List.of(product));

        consumer.consumeOrderConfirmationNotifications(order, 0, 200L, acknowledgment);

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().getType()).isEqualTo(NotificationType.ORDER_CONFIRMATION);
        assertThat(notifCaptor.getValue().getOrderConfirmation()).isEqualTo(order);

        verify(emailService).sendOrderConfirmationEmail(
                "ana@example.com", "Ana Silva", BigDecimal.valueOf(1000), "REF-ORD", order);

        verify(acknowledgment).acknowledge();
    }
}
