package code.with.vanilson.notification.idempotency;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "processed_events")
@Getter
public class ProcessedEvent {

    @Id
    private String id; // "topic:partition:offset"

    private String topic;

    @Indexed(expireAfterSeconds = 86400) // TTL: 24 hours
    private LocalDateTime processedAt;

    private ProcessedEvent() {}

    public static ProcessedEvent of(String topic, int partition, long offset) {
        ProcessedEvent e = new ProcessedEvent();
        e.id = topic + ":" + partition + ":" + offset;
        e.topic = topic;
        e.processedAt = LocalDateTime.now();
        return e;
    }
}
