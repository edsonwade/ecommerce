package code.with.vanilson.orderservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("outbox.queue.depth", outboxRepository, OutboxRepository::countPendingEvents)
                .description("Number of events awaiting Kafka publication in outbox")
                .register(meterRegistry);
    }

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
                // (ensures all events for the same order go to the same partition).
                // Blocking send: the async whenComplete callback could fire after the
                // scheduler run (and its transaction) ended, leaving events PENDING
                // forever on shutdown and ignoring Kafka backpressure. One event at a
                // time with confirmed delivery keeps the status write in this transaction.
                SendResult<String, String> result = kafkaTemplate
                        .send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                handlePublishSuccess(event, result);

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                handlePublishFailure(event, ex);
                return; // shutting down — stop processing, remaining events stay PENDING
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
        meterRegistry.counter("outbox.publish.count", "outcome", "success").increment();
    }

    @Transactional
    protected void handlePublishFailure(OutboxEvent event, Throwable ex) {
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= 5) {
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            log.error("[OutboxPublisher] Event permanently failed after 5 retries: eventId=[{}] error=[{}]",
                    event.getEventId(), ex.getMessage());
            meterRegistry.counter("outbox.publish.count", "outcome", "failure").increment();
        } else {
            log.warn("[OutboxPublisher] Event publish failed (attempt {}/5): eventId=[{}] error=[{}]",
                    event.getRetryCount(), event.getEventId(), ex.getMessage());
        }
        outboxRepository.save(event);
    }
}
