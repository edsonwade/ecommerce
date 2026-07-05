package code.with.vanilson.notification.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Slice test for the Kafka consumer configuration.
 *
 * Verifies the settings that prevent a poison record from hot-looping the consumer:
 * - ErrorHandlingDeserializer wraps JsonDeserializer, so a record that cannot be
 *   deserialized surfaces as a handled DeserializationException instead of being
 *   retried forever inside the poll loop
 * - A default type is configured for records produced without type headers
 * - The listener container keeps MANUAL_IMMEDIATE ack and a common error handler
 */
@DisplayName("NotificationConfig — Kafka consumer slice tests")
class NotificationConfigTest {

    private NotificationConfig config;

    @BeforeEach
    void setUp() {
        config = new NotificationConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
    }

    @Test
    @DisplayName("value deserializer is ErrorHandlingDeserializer delegating to JsonDeserializer")
    void valueDeserializerIsErrorHandlingWrapper() {
        Map<String, Object> props = config.consumerFactory().getConfigurationProperties();

        assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(ErrorHandlingDeserializer.class);
        assertThat(props.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS))
                .isEqualTo(JsonDeserializer.class.getName());
    }

    @Test
    @DisplayName("default type is configured for records without type headers")
    void defaultTypeConfiguredForHeaderlessRecords() {
        Map<String, Object> props = config.consumerFactory().getConfigurationProperties();

        assertThat(props.get(JsonDeserializer.USE_TYPE_INFO_HEADERS)).isEqualTo(false);
        assertThat((String) props.get(JsonDeserializer.VALUE_DEFAULT_TYPE)).isNotBlank();
    }

    @Test
    @DisplayName("listener container keeps MANUAL_IMMEDIATE ack and a common error handler")
    void listenerContainerKeepsAckModeAndErrorHandler() {
        ConsumerFactory<String, Object> consumerFactory = config.consumerFactory();
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.kafkaListenerContainerFactory(consumerFactory, kafkaTemplate);

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        assertThat(ReflectionTestUtils.getField(factory, "commonErrorHandler")).isNotNull();
    }
}
