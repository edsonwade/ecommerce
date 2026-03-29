package code.with.vanilson.orderservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OutboxEventPublisher — Infrastructure Layer (Outbox Pattern)
 * <p>
 * Scheduled job that polls the outbox_event table and publishes
 * pending events to Kafka. Runs every 5 seconds.
 * <p>
 * Guarantees:
 * - AT-LEAST-ONCE delivery to Kafka (duplicates handled by consumer idempotency)
 * - Events are published in creation order (FIFO within the same correlationId)
 * - After 5 failed retries, event is marked FAILED for manual intervention
 * <p>
 * Trade-off: 5-second polling delay is acceptable for Phase 3.
 * Phase 4 option: Replace with Debezium CDC for near-zero latency.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxRepository  outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Polls for pending outbox events every 5 seconds.
     * Uses @Scheduled(fixedDelay) so the next run starts AFTER the previous one completes
     * — prevents concurrent runs even if processing is slow.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEvents();

        if (pendingEvents.isEmpty()) {
            return; // nothing to do — avoid log noise
        }

        log.info("[OutboxPublisher] Processing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Send to Kafka with correlationId as partition key
                // (ensures all events for the same order go to the same partition)
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                handlePublishFailure(event, ex);
                            } else {
                                handlePublishSuccess(event, result);
                            }
                        });

            } catch (Exception ex) {
                handlePublishFailure(event, ex);
            }
        }
    }

    @Transactional
    protected void handlePublishSuccess(OutboxEvent event, SendResult<String, String> result) {
        event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(event);
        log.info("[OutboxPublisher] Event published: eventId=[{}] topic=[{}] partition=[{}] offset=[{}]",
                event.getEventId(), event.getTopic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }

    @Transactional
    protected void handlePublishFailure(OutboxEvent event, Throwable ex) {
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= 5) {
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            log.error("[OutboxPublisher] Event permanently failed after 5 retries: eventId=[{}] error=[{}]",
                    event.getEventId(), ex.getMessage());
        } else {
            log.warn("[OutboxPublisher] Event publish failed (attempt {}/5): eventId=[{}] error=[{}]",
                    event.getRetryCount(), event.getEventId(), ex.getMessage());
        }
        outboxRepository.save(event);
    }
}
