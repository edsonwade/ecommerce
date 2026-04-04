package code.with.vanilson.notification.idempotency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedEventTest {

    @Test
    void eventId_isCompositeOf_topicPartitionOffset() {
        ProcessedEvent event = ProcessedEvent.of("payment-topic", 0, 42L);
        assertThat(event.getId()).isEqualTo("payment-topic:0:42");
        assertThat(event.getTopic()).isEqualTo("payment-topic");
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    void eventId_uniquenessByPartitionAndOffset() {
        ProcessedEvent e1 = ProcessedEvent.of("payment-topic", 0, 1L);
        ProcessedEvent e2 = ProcessedEvent.of("payment-topic", 1, 1L);
        assertThat(e1.getId()).isNotEqualTo(e2.getId()); // same offset, different partition
    }
}
