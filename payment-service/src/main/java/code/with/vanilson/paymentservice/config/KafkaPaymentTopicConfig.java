package code.with.vanilson.paymentservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * KafkaPaymentTopicConfig — Infrastructure Layer
 * <p>
 * Declares Kafka topics owned by payment-service.
 * Topics are auto-created on startup if absent.
 * <p>
 * payment-topic: published after successful payment — triggers email notification.
 * payment-topic.DLQ: receives events after all retry attempts are exhausted.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Configuration
public class KafkaPaymentTopicConfig {

    @Bean
    public NewTopic paymentTopic() {
        return TopicBuilder
                .name("payment-topic")
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentTopicDlq() {
        return TopicBuilder
                .name("payment-topic.DLQ")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
