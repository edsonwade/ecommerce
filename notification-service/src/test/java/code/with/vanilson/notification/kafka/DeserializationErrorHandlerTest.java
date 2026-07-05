package code.with.vanilson.notification.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit test for DeserializationErrorHandler.
 * Verifies that poison records (unparseable JSON) are logged and skipped,
 * preventing hot retry loops.
 */
@DisplayName("DeserializationErrorHandler — Unit Tests")
@Slf4j
class DeserializationErrorHandlerTest {

    @Test
    @DisplayName("should recover from deserialization error by logging and skipping")
    void shouldRecoverFromDeserializationError() {
        ConsumerRecordRecoverer recoverer = new DeserializationErrorHandler();
        ConsumerRecord<String, byte[]> poisonRecord = new ConsumerRecord<>(
                "payment-topic", 0, 100L, "key", "{INVALID_JSON}".getBytes());

        assertThatNoException().isThrownBy(() ->
                recoverer.accept(poisonRecord, new IllegalStateException(
                        "No type information in headers and no default type provided")));
    }

    @Test
    @DisplayName("should handle null payload in poison record")
    void shouldHandleNullPayload() {
        ConsumerRecordRecoverer recoverer = new DeserializationErrorHandler();
        ConsumerRecord<String, byte[]> nullRecord = new ConsumerRecord<>(
                "order-topic", 1, 50L, "key", null);

        assertThatNoException().isThrownBy(() ->
                recoverer.accept(nullRecord, new RuntimeException("Deserialization failed")));
    }

    @Test
    @DisplayName("should handle DLQ poison records without retrying")
    void shouldHandleDlqPoisonRecords() {
        ConsumerRecordRecoverer recoverer = new DeserializationErrorHandler();
        ConsumerRecord<String, byte[]> dlqRecord = new ConsumerRecord<>(
                "payment-topic.DLQ", 2, 75L, "key", "GARBAGE".getBytes());

        assertThatNoException().isThrownBy(() ->
                recoverer.accept(dlqRecord, new IllegalStateException("Type mismatch")));
    }
}
