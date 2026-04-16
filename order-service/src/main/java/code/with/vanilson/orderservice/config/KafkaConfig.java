package code.with.vanilson.orderservice.config;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
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
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import code.with.vanilson.orderservice.kafka.OrderConfirmation;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaConfig — Infrastructure Layer
 * <p>
 * Configures:
 * 1. String KafkaTemplate for OutboxEventPublisher (publishes serialised JSON strings)
 * 2. OrderConfirmation KafkaTemplate for OrderProducer (typed JSON serialisation)
 * 3. Saga consumer factory with MANUAL_IMMEDIATE ack + DLQ error handler
 * 4. Graceful shutdown: setStopImmediate(false) drains the current batch before the JVM exits
 * <p>
 * All factories are built on top of {@link KafkaProperties#buildProducerProperties} /
 * {@link KafkaProperties#buildConsumerProperties} so SASL, security protocol, and
 * bootstrap-servers are inherited from the YAML config rather than hard-coded here.
 * </p>
 *
 * @author vamuhong
 * @version 3.2
 */
@Configuration
public class KafkaConfig {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    // -------------------------------------------------------
    // String KafkaTemplate — for OutboxEventPublisher
    // -------------------------------------------------------

    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        // buildProducerProperties includes bootstrap-servers, SASL config,
        // idempotence settings, and serializers from YAML
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    // -------------------------------------------------------
    // OrderConfirmation KafkaTemplate — for OrderProducer
    // -------------------------------------------------------

    @Bean
    public ProducerFactory<String, OrderConfirmation> orderConfirmationProducerFactory() {
        // Inherit all producer properties (SASL, bootstrap-servers, idempotence)
        // but override value serializer to produce typed JSON
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, OrderConfirmation> orderConfirmationKafkaTemplate() {
        return new KafkaTemplate<>(orderConfirmationProducerFactory());
    }

    // -------------------------------------------------------
    // Saga Consumer Factory — for OrderSagaConsumer
    // -------------------------------------------------------

    @Bean
    public ConsumerFactory<String, Object> sagaConsumerFactory() {
        // buildConsumerProperties includes bootstrap-servers, group-id, SASL config,
        // deserializers, and offset settings from YAML
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
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
