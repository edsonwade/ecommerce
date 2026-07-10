package code.with.vanilson.notification.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

/**
 * Recoverer for deserialization errors in Kafka consumer.
 *
 * When a record cannot be deserialized (e.g., poison record with invalid JSON
 * or missing type headers), this recoverer logs the error and skips the record
 * instead of retrying infinitely, preventing hot retry loops.
 *
 * This is paired with ErrorHandlingDeserializer in the consumer config to ensure
 * deserialization errors are caught and handled gracefully.
 */
@Slf4j
public class DeserializationErrorHandler implements ConsumerRecordRecoverer {

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Deserialization error: unable to deserialize record from topic='{}', partition={}, offset={}. "
                        + "Record will be skipped. Exception: {}",
                record.topic(), record.partition(), record.offset(), exception.getMessage());
        log.debug("Deserialization error stack trace", exception);
    }
}
