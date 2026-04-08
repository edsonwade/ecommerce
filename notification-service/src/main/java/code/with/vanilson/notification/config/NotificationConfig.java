package code.with.vanilson.notification.config;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * NotificationConfig — Infrastructure Layer
 * <p>
 * Configures:
 * 1. MessageSource — resolves messages.properties in all notification classes
 * 2. Kafka consumer factory with MANUAL_IMMEDIATE acknowledgement
 *    → offsets are committed only after email + PDF processing succeeds
 *    → if processing fails, Kafka retries the message automatically
 * 3. Dead Letter Queue error handler
 *    → after 3 retry attempts, failed events go to {topic}.DLQ
 * 4. Async email processing thread pool
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Configuration
public class NotificationConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    // -------------------------------------------------------
    // MessageSource
    // -------------------------------------------------------

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    // -------------------------------------------------------
    // Kafka Consumer Factory (manual ack, DLQ on failure)
    // -------------------------------------------------------

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Manual acknowledgement — do NOT auto-commit offsets
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "code.with.vanilson.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Listener container factory with:
     * - MANUAL_IMMEDIATE ack mode → offset committed only after @KafkaListener method returns
     * - setStopImmediate(false): finish processing the current poll batch before stopping
     * - DefaultErrorHandler with FixedBackOff(1000ms, 3 retries) → after 3 failures sends to DLQ
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // MANUAL_IMMEDIATE: offset committed immediately when Acknowledgment.acknowledge() is called
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setStopImmediate(false);

        // Error handler: retry 3x with 1s delay, then route to {topic}.DLQ
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLQ", record.partition()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(1000L, 3L));

        factory.setCommonErrorHandler(errorHandler);

        // Concurrency = 3: one thread per partition group (payment, order)
        factory.setConcurrency(3);
        return factory;
    }

    /**
     * Graceful shutdown — called during Spring context close (SIGTERM / actuator /shutdown).
     * Stops all listener containers so in-flight messages are acknowledged before the JVM exits.
     * Works with server.shutdown=graceful and spring.lifecycle.timeout-per-shutdown-phase=30s.
     */
    @PreDestroy
    public void onDestroy() {
        kafkaListenerEndpointRegistry.getListenerContainers()
                .forEach(container -> {
                    if (container.isRunning()) {
                        container.stop();
                    }
                });
    }
}
