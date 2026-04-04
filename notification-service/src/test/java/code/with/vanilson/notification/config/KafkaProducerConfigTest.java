package code.with.vanilson.notification.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    @Test
    void producerFactory_disablesTypeInfoHeaders() {
        KafkaProducerConfig config = new KafkaProducerConfig("localhost:9092");
        Map<String, Object> props = ((DefaultKafkaProducerFactory<?, ?>) config.producerFactory()).getConfigurationProperties();
        assertThat(props.get(JsonSerializer.ADD_TYPE_INFO_HEADERS)).isEqualTo(false);
    }

    @Test
    void adminClient_isCreated_withoutConnecting() {
        KafkaProducerConfig config = new KafkaProducerConfig("localhost:9092");
        try (AdminClient client = config.adminClient()) {
            assertThat(client).isNotNull();
        }
        // try-with-resources confirms AdminClient.close() is callable — verifying destroyMethod works
    }

    @Test
    void producerFactory_usesCorrectBootstrapServers() {
        KafkaProducerConfig config = new KafkaProducerConfig("broker1:9092");
        Map<String, Object> props = ((DefaultKafkaProducerFactory<?, ?>) config.producerFactory()).getConfigurationProperties();
        assertThat(props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("broker1:9092");
        assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(JsonSerializer.class);
    }
}
