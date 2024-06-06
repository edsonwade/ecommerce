package code.with.vanilson.paymentservice.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRetry
public class KafkaPaymentTopicConfig {

    @Retryable(maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2),
            retryFor = org.springframework.kafka.KafkaException.class)
    @Bean
    public NewTopic paymentTopic() {
        return TopicBuilder
                .name("payment-topic")
                .build();
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092"); // Update with your Kafka bootstrap servers
        return new KafkaAdmin(configs);
    }
}