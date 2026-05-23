package code.with.vanilson.authentication.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaAuthConfig — Kafka producer configuration for authentication-service.
 * <p>
 * Provides a {@link KafkaTemplate}{@code <String, Object>} for {@link
 * code.with.vanilson.authentication.infrastructure.kafka.UserRegisteredProducer}.
 * Bootstrap servers and SASL config are inherited from application YAML via
 * {@link KafkaProperties}.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Configuration
public class KafkaAuthConfig {

    @Bean
    public ProducerFactory<String, Object> authProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> authProducerFactory) {
        return new KafkaTemplate<>(authProducerFactory);
    }
}
