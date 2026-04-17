package code.with.vanilson.paymentservice.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static org.springframework.kafka.support.KafkaHeaders.TOPIC;

/**
 * NotificationProducer — Infrastructure Layer (Messaging)
 * <p>
 * Publishes payment confirmation events to the Kafka 'payment-topic'.
 * Consumed by notification-service to send confirmation emails.
 * <p>
 * Single Responsibility (SOLID-S): only handles Kafka publishing for payment notifications.
 * Dependency Inversion (SOLID-D): depends on KafkaTemplate abstraction, not on concrete brokers.
 * <p>
 * Uses async send with callbacks for non-blocking I/O.
 * All log messages resolved from messages.properties via MessageSource.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class NotificationProducer {

    private static final String PAYMENT_TOPIC = "payment-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessageSource messageSource;

    public NotificationProducer(@Qualifier("paymentSagaKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                                 MessageSource messageSource) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageSource = messageSource;
    }

    /**
     * Sends a payment confirmation notification to Kafka asynchronously.
     *
     * @param request the payment notification payload
     */
    public void sendNotification(PaymentNotificationRequest request) {
        log.info(messageSource.getMessage(
                "payment.log.notification.sending",
                new Object[]{request.orderReference(), request.customerEmail()},
                LocaleContextHolder.getLocale()));

        Message<PaymentNotificationRequest> message = MessageBuilder
                .withPayload(request)
                .setHeader(TOPIC, PAYMENT_TOPIC)
                .setHeader("orderReference", request.orderReference())
                .build();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error(messageSource.getMessage(
                        "payment.notification.failed",
                        new Object[]{request.orderReference(), ex.getMessage()},
                        LocaleContextHolder.getLocale()));
            } else {
                log.info(messageSource.getMessage(
                        "payment.notification.sent",
                        new Object[]{request.orderReference()},
                        LocaleContextHolder.getLocale()));
            }
        });
    }
}
