package code.with.vanilson.notification;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "dlq_events")
@Getter
public class DlqEvent {

    @Id
    private String id; // "topic:partition:offset"

    private String topic;
    private int partition;
    private long offset;
    private String payload;
    private LocalDateTime receivedAt;

    private DlqEvent() {}

    public static DlqEvent of(String topic, int partition, long offset, Object payload) {
        DlqEvent e = new DlqEvent();
        e.id = topic + ":" + partition + ":" + offset;
        e.topic = topic;
        e.partition = partition;
        e.offset = offset;
        e.payload = payload != null ? payload.toString() : "null";
        e.receivedAt = LocalDateTime.now();
        return e;
    }
}
