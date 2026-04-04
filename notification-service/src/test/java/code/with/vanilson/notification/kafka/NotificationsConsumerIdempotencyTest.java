package code.with.vanilson.notification.kafka;

import code.with.vanilson.notification.NotificationRepository;
import code.with.vanilson.notification.email.EmailService;
import code.with.vanilson.notification.idempotency.ProcessedEvent;
import code.with.vanilson.notification.idempotency.ProcessedEventRepository;
import code.with.vanilson.notification.kafka.order.OrderConfirmation;
import code.with.vanilson.notification.kafka.payment.PaymentConfirmation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.List;
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

    @Test
    void duplicateOrderEvent_isSkipped_emailNotSentAgain() {
        OrderConfirmation.CustomerSummary customer = new OrderConfirmation.CustomerSummary(
                "cust-1", "Jane", "Doe", "jane@example.com");
        OrderConfirmation duplicate = new OrderConfirmation(
                "ORD-003", new BigDecimal("199.99"), "CREDIT_CARD", customer, List.of());

        when(processedEventRepository.findById("order-topic:0:5"))
                .thenReturn(Optional.of(ProcessedEvent.of("order-topic", 0, 5L)));
        when(messageSource.getMessage(any(), any(), any())).thenReturn("msg");

        consumer.consumeOrderConfirmationNotifications(duplicate, 0, 5L, ack);

        verify(emailService, never()).sendOrderConfirmationEmail(any(), any(), any(), any(), any());
        verify(repository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void newOrderEvent_isProcessed_andMarkedAsProcessed() {
        OrderConfirmation.CustomerSummary customer = new OrderConfirmation.CustomerSummary(
                "cust-2", "Bob", "Smith", "bob@example.com");
        OrderConfirmation newEvent = new OrderConfirmation(
                "ORD-004", new BigDecimal("299.99"), "DEBIT_CARD", customer, List.of());

        when(processedEventRepository.findById("order-topic:0:6"))
                .thenReturn(Optional.empty());
        when(messageSource.getMessage(any(), any(), any())).thenReturn("msg");

        consumer.consumeOrderConfirmationNotifications(newEvent, 0, 6L, ack);

        verify(emailService).sendOrderConfirmationEmail(any(), any(), any(), any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(ack).acknowledge();
    }
}
