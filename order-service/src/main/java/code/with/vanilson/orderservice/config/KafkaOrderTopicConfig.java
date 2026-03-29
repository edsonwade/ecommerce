package code.with.vanilson.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * KafkaOrderTopicConfig — Infrastructure Layer (Phase 3 update)
 * <p>
 * Declares ALL Kafka topics relevant to the order saga.
 * order-service is the topic owner — it creates them on startup.
 * Other services subscribe to these topics.
 * <p>
 * Production settings (override via env):
 * - partitions: 50 per topic (100k+ msg/s throughput)
 * - replicas:   3 (HA — survives 1 broker failure)
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Configuration
public class KafkaOrderTopicConfig {

    // -------------------------------------------------------
    // Phase 1 topics (kept for notification-service compatibility)
    // -------------------------------------------------------

    @Bean public NewTopic orderTopic() {
        return TopicBuilder.name("order-topic").partitions(10).replicas(1).build();
    }

    @Bean public NewTopic orderTopicDlq() {
        return TopicBuilder.name("order-topic.DLQ").partitions(3).replicas(1).build();
    }

    // -------------------------------------------------------
    // Phase 3 — Saga topics
    // -------------------------------------------------------

    /** Published by order-service outbox → consumed by product-service */
    @Bean public NewTopic orderRequestedTopic() {
        return TopicBuilder.name("order.requested").partitions(10).replicas(1).build();
    }

    @Bean public NewTopic orderRequestedDlq() {
        return TopicBuilder.name("order.requested.DLQ").partitions(3).replicas(1).build();
    }

    /** Published by product-service → consumed by payment-service */
    @Bean public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name("inventory.reserved").partitions(10).replicas(1).build();
    }

    /** Published by product-service → consumed by order-service (cancel path) */
    @Bean public NewTopic inventoryInsufficientTopic() {
        return TopicBuilder.name("inventory.insufficient").partitions(10).replicas(1).build();
    }

    @Bean public NewTopic inventoryInsufficientDlq() {
        return TopicBuilder.name("inventory.insufficient.DLQ").partitions(3).replicas(1).build();
    }

    /** Published by payment-service → consumed by order-service (confirm path) */
    @Bean public NewTopic paymentAuthorizedTopic() {
        return TopicBuilder.name("payment.authorized").partitions(10).replicas(1).build();
    }

    @Bean public NewTopic paymentAuthorizedDlq() {
        return TopicBuilder.name("payment.authorized.DLQ").partitions(3).replicas(1).build();
    }

    /** Published by payment-service → consumed by order-service + product-service (cancel path) */
    @Bean public NewTopic paymentFailedTopic() {
        return TopicBuilder.name("payment.failed").partitions(10).replicas(1).build();
    }

    @Bean public NewTopic paymentFailedDlq() {
        return TopicBuilder.name("payment.failed.DLQ").partitions(3).replicas(1).build();
    }

    /** Published by product-service after releasing stock (compensation) */
    @Bean public NewTopic inventoryReleasedTopic() {
        return TopicBuilder.name("inventory.released").partitions(10).replicas(1).build();
    }
}
