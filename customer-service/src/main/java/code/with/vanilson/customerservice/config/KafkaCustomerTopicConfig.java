package code.with.vanilson.customerservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * KafkaCustomerTopicConfig — declares Kafka topics owned by customer-service.
 * <p>
 * {@code customer.profile} — published on every customer create/update.
 * Consumed by order-service to maintain a local CustomerSnapshot (CQRS read model).
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Configuration
public class KafkaCustomerTopicConfig {

    @Bean
    public NewTopic customerProfileTopic() {
        return TopicBuilder.name("customer.profile").partitions(10).replicas(1).build();
    }

    @Bean
    public NewTopic customerProfileDlq() {
        return TopicBuilder.name("customer.profile.DLQ").partitions(3).replicas(1).build();
    }
}
