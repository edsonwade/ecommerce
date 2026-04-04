package code.with.vanilson.notification.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    @Test
    void kafkaTemplate_isCreated_withStringJsonSerializer() {
        KafkaProducerConfig config = new KafkaProducerConfig("localhost:9092");
        KafkaTemplate<String, Object> template = config.kafkaTemplate();
        assertThat(template).isNotNull();
        assertThat(template.getProducerFactory()).isInstanceOf(DefaultKafkaProducerFactory.class);
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
