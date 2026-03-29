package code.with.vanilson.orderservice.kafka;

import lombok.extern.slf4j.Slf4j;
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
 * OrderProducer — Infrastructure Layer (Messaging)
 * <p>
 * Publishes order confirmation events to the Kafka 'order-topic'.
 * Implements Single Responsibility Principle (SOLID-S):
 * this class has exactly one reason to change — Kafka publishing logic.
 * <p>
 * Uses async send with callback logging (non-blocking).
 * All log messages resolved from messages.properties via MessageSource.
 * OrderConfirmation uses local DTOs only — no cross-service class imports.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class OrderProducer {

    private static final String ORDER_TOPIC = "order-topic";

    private final KafkaTemplate<String, OrderConfirmation> kafkaTemplate;
    private final MessageSource messageSource;

    public OrderProducer(KafkaTemplate<String, OrderConfirmation> kafkaTemplate,
                         MessageSource messageSource) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageSource = messageSource;
    }

    /**
     * Sends an order confirmation event to Kafka asynchronously.
     * The send is fire-and-forget with success/failure callbacks for observability.
     * If the send fails, it is logged as an error — in Phase 3 this will be
     * replaced by an outbox pattern to guarantee delivery.
     *
     * @param orderConfirmation the order confirmation event payload
     */
    public void sendOrderConfirmation(OrderConfirmation orderConfirmation) {
        log.info(messageSource.getMessage(
                "order.log.confirmation.sent",
                new Object[]{orderConfirmation.orderReference()},
                LocaleContextHolder.getLocale()));

        Message<OrderConfirmation> message = MessageBuilder
                .withPayload(orderConfirmation)
                .setHeader(TOPIC, ORDER_TOPIC)
                .setHeader("orderReference", orderConfirmation.orderReference())
                .build();

        CompletableFuture<SendResult<String, OrderConfirmation>> future =
                kafkaTemplate.send(message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[OrderProducer] Failed to send order confirmation for ref=[{}]. Error: {}",
                        orderConfirmation.orderReference(), ex.getMessage(), ex);
            } else {
                log.info("[OrderProducer] Order confirmation sent successfully: ref=[{}] partition=[{}] offset=[{}]",
                        orderConfirmation.orderReference(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
