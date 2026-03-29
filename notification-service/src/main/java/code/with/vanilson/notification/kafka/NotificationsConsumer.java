package code.with.vanilson.notification.kafka;

import code.with.vanilson.notification.Notification;
import code.with.vanilson.notification.NotificationRepository;
import code.with.vanilson.notification.NotificationType;
import code.with.vanilson.notification.email.EmailService;
import code.with.vanilson.notification.kafka.order.OrderConfirmation;
import code.with.vanilson.notification.kafka.payment.PaymentConfirmation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * NotificationsConsumer — Infrastructure Layer (Kafka Consumer)
 * <p>
 * Consumes payment and order events from Kafka and triggers email notifications.
 * <p>
 * KEY CHANGES FROM ORIGINAL:
 * 1. Uses local DTOs (OrderConfirmation, PaymentConfirmation) — no cross-service JAR.
 * 2. Manual acknowledgment (AckMode.MANUAL_IMMEDIATE) — message is only committed
 *    AFTER email sending completes. If email fails, Kafka retries the message.
 * 3. Log messages from messages.properties via MessageSource.
 * 4. sendOrderConfirmationEmail now passes the full OrderConfirmation object
 *    so EmailService can generate the PDF invoice.
 * 5. MessagingException is now caught inside EmailService (@Async) — consumer
 *    thread is never blocked by email I/O.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationsConsumer {

    private final NotificationRepository repository;
    private final EmailService emailService;
    private final MessageSource messageSource;

    @KafkaListener(
            topics = "payment-topic",
            groupId = "paymentGroup",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumePaymentSuccessNotifications(
            @Payload PaymentConfirmation paymentConfirmation,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info(msg("notification.log.consumed.payment",
                paymentConfirmation.orderReference(), partition, offset));

        repository.save(Notification.builder()
                .type(NotificationType.PAYMENT_CONFIRMATION)
                .notificationDate(LocalDateTime.now())
                .paymentConfirmation(paymentConfirmation)
                .build());

        String customerName = paymentConfirmation.customerFirstname()
                + " " + paymentConfirmation.customerLastname();

        emailService.sendPaymentSuccessEmail(
                paymentConfirmation.customerEmail(),
                customerName,
                paymentConfirmation.amount(),
                paymentConfirmation.orderReference());

        ack.acknowledge(); // commit offset only after processing
    }

    @KafkaListener(
            topics = "order-topic",
            groupId = "orderGroup",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrderConfirmationNotifications(
            @Payload OrderConfirmation orderConfirmation,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info(msg("notification.log.consumed.order",
                orderConfirmation.orderReference(), partition, offset));

        repository.save(Notification.builder()
                .type(NotificationType.ORDER_CONFIRMATION)
                .notificationDate(LocalDateTime.now())
                .orderConfirmation(orderConfirmation)
                .build());

        OrderConfirmation.CustomerSummary customer = orderConfirmation.customer();
        String customerName = customer.firstname() + " " + customer.lastname();

        emailService.sendOrderConfirmationEmail(
                customer.email(),
                customerName,
                orderConfirmation.totalAmount(),
                orderConfirmation.orderReference(),
                orderConfirmation); // full object for PDF invoice generation

        ack.acknowledge();
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
