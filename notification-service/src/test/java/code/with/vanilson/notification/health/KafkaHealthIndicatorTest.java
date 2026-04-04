package code.with.vanilson.notification.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {

    @Mock AdminClient adminClient;
    @Mock ListTopicsResult listTopicsResult;
    @Mock KafkaFuture<Set<String>> topicsFuture;

    @Test
    void health_isUp_whenKafkaResponds() throws Exception {
        when(adminClient.listTopics()).thenReturn(listTopicsResult);
        when(listTopicsResult.names()).thenReturn(topicsFuture);
        when(topicsFuture.get(3, TimeUnit.SECONDS))
                .thenReturn(Set.of("payment-topic", "order-topic"));

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(adminClient);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("topicCount");
        assertThat(health.getDetails().get("topicCount")).isEqualTo(2);
    }

    @Test
    void health_isDown_whenKafkaThrows() throws Exception {
        when(adminClient.listTopics()).thenReturn(listTopicsResult);
        when(listTopicsResult.names()).thenReturn(topicsFuture);
        when(topicsFuture.get(3, TimeUnit.SECONDS))
                .thenThrow(new ExecutionException("Connection refused", new RuntimeException()));

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(adminClient);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
