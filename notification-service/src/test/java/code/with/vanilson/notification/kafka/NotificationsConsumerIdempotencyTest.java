package code.with.vanilson.notification.kafka;

import code.with.vanilson.notification.NotificationRepository;
import code.with.vanilson.notification.email.EmailService;
import code.with.vanilson.notification.idempotency.ProcessedEvent;
import code.with.vanilson.notification.idempotency.ProcessedEventRepository;
import code.with.vanilson.notification.kafka.payment.PaymentConfirmation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationsConsumerIdempotencyTest {

    @Mock NotificationRepository repository;
    @Mock EmailService emailService;
    @Mock MessageSource messageSource;
    @Mock ProcessedEventRepository processedEventRepository;
    @Mock Acknowledgment ack;
    @InjectMocks NotificationsConsumer consumer;

    @Test
    void duplicatePaymentEvent_isSkipped_emailNotSentAgain() {
        PaymentConfirmation duplicate = new PaymentConfirmation(
                "ORD-001", new BigDecimal("99.99"),
                "PAYMENT_SUCCESS", "jane@example.com", "Jane", "Doe");

        when(processedEventRepository.findById("payment-topic:0:1"))
                .thenReturn(Optional.of(ProcessedEvent.of("payment-topic", 0, 1L)));
        when(messageSource.getMessage(any(), any(), any())).thenReturn("msg");

        consumer.consumePaymentSuccessNotifications(duplicate, 0, 1L, ack);

        verify(emailService, never()).sendPaymentSuccessEmail(any(), any(), any(), any());
        verify(repository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void newPaymentEvent_isProcessed_andMarkedAsProcessed() {
        PaymentConfirmation newEvent = new PaymentConfirmation(
                "ORD-002", new BigDecimal("50.00"),
                "PAYMENT_SUCCESS", "bob@example.com", "Bob", "Smith");

        when(processedEventRepository.findById("payment-topic:0:2"))
                .thenReturn(Optional.empty());
        when(messageSource.getMessage(any(), any(), any())).thenReturn("msg");

        consumer.consumePaymentSuccessNotifications(newEvent, 0, 2L, ack);

        verify(emailService).sendPaymentSuccessEmail(any(), any(), any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(ack).acknowledge();
    }
}
