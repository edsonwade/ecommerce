package code.with.vanilson.orderservice.config;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaConfig — Infrastructure Layer
 * <p>
 * Configures:
 * 1. String KafkaTemplate for OutboxEventPublisher (publishes serialised JSON strings)
 * 2. Saga consumer factory with MANUAL_IMMEDIATE ack + DLQ error handler
 * 3. Graceful shutdown: setStopImmediate(false) drains the current batch before the JVM exits
 * <p>
 * Two separate KafkaTemplate beans:
 * - String KafkaTemplate: used by OutboxEventPublisher (payload already serialised)
 * - Typed KafkaTemplates: used by OrderProducer (OrderConfirmation events)
 * </p>
 *
 * @author vamuhong
 * @version 3.1
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.producer.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    // -------------------------------------------------------
    // String KafkaTemplate — for OutboxEventPublisher
    // -------------------------------------------------------

    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    // -------------------------------------------------------
    // Saga Consumer Factory — for OrderSagaConsumer
    // -------------------------------------------------------

    @Bean
    public ConsumerFactory<String, Object> sagaConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-saga-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "code.with.vanilson.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Saga listener container factory:
     * - MANUAL_IMMEDIATE ack: offset committed after DB update succeeds
     * - setStopImmediate(false): finish processing the current poll batch before stopping
     * - DLQ: failed events go to {topic}.DLQ after 3 retries with 1s delay
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> sagaKafkaListenerContainerFactory(
            KafkaTemplate<String, String> stringKafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(sagaConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setStopImmediate(false);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                stringKafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLQ", record.partition()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(1000L, 3L));

        factory.setCommonErrorHandler(errorHandler);
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
