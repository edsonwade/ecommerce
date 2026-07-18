package code.with.vanilson.paymentservice.infrastructure.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PaymentOutboxPublisher — Infrastructure Layer (Transactional Outbox, Fase 6.1).
 * <p>
 * Localised clone of order-service's {@code OutboxEventPublisher}. Drains PENDING
 * {@link PaymentOutboxEvent} rows to Kafka off the HTTP request thread, so the
 * refund endpoint no longer pays the first-send producer init / metadata fetch.
 * <p>
 * Guarantees: AT-LEAST-ONCE delivery (consumers dedupe via eventId); FIFO by
 * {@code created_at}; after 5 failed retries a row is marked {@code FAILED}
 * (no publisher-side DLQ — matching this project's outbox convention; a FAILED
 * refund event is surfaced via metrics + alerting instead).
 * <p>
 * Config knobs (all optional, with defaults):
 * <ul>
 *   <li>{@code payment.outbox.poll-interval-ms} (5000) — scheduler cadence</li>
 *   <li>{@code payment.outbox.batch-size} (100) — max rows drained per tick</li>
 *   <li>{@code payment.outbox.retention-days} (7) — how long PUBLISHED rows are kept</li>
 *   <li>{@code payment.outbox.purge-cron} (0 0 3 * * *) — purge schedule</li>
 * </ul>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Service
public class PaymentOutboxPublisher {

    private static final int MAX_RETRIES = 5;

    private final PaymentOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final MessageSource messageSource;

    private final int batchSize;
    private final int retentionDays;

    public PaymentOutboxPublisher(
            PaymentOutboxRepository outboxRepository,
            @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> outboxKafkaTemplate,
            MeterRegistry meterRegistry,
            MessageSource messageSource,
            @org.springframework.beans.factory.annotation.Value("${payment.outbox.batch-size:100}") int batchSize,
            @org.springframework.beans.factory.annotation.Value("${payment.outbox.retention-days:7}") int retentionDays) {
        this.outboxRepository = outboxRepository;
        this.outboxKafkaTemplate = outboxKafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.messageSource = messageSource;
        this.batchSize = batchSize;
        this.retentionDays = retentionDays;
    }

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("payment.outbox.queue.depth", outboxRepository, PaymentOutboxRepository::countPending)
                .description("Number of payment.refunded events awaiting Kafka publication")
                .register(meterRegistry);
    }

    /**
     * Drains a bounded FIFO batch of PENDING events to Kafka. {@code fixedDelay} so the
     * next run starts only after the previous completes (no overlapping runs).
     */
    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<PaymentOutboxEvent> pending =
                outboxRepository.findPendingBatch(PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return; // nothing to do — no log noise
        }

        for (PaymentOutboxEvent event : pending) {
            try {
                SendResult<String, String> result = outboxKafkaTemplate
                        .send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                handleSuccess(event, result);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                handleFailure(event, ex);
                return; // shutting down — leave the rest PENDING
            } catch (Exception ex) {
                handleFailure(event, ex);
            }
        }
    }

    private void handleSuccess(PaymentOutboxEvent event, SendResult<String, String> result) {
        event.setStatus(PaymentOutboxEvent.OutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(event);
        log.info(messageSource.getMessage("payment.outbox.published",
                new Object[]{event.getEventId(), event.getTopic(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset()},
                LocaleContextHolder.getLocale()));
        meterRegistry.counter("payment.outbox.publish.count", "outcome", "success").increment();
    }

    private void handleFailure(PaymentOutboxEvent event, Throwable ex) {
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.setStatus(PaymentOutboxEvent.OutboxStatus.FAILED);
            // Terminal: money was refunded but the order was never told. Must be visible.
            log.error(messageSource.getMessage("payment.outbox.publish.failed",
                    new Object[]{event.getEventId(), event.getCorrelationId()},
                    LocaleContextHolder.getLocale()));
            if (log.isDebugEnabled()) {
                log.debug("[PaymentOutbox] terminal failure cause", ex);
            }
            meterRegistry.counter("payment.outbox.publish.count", "outcome", "failure").increment();
        }
        outboxRepository.save(event);
    }

    /**
     * Retention purge — removes PUBLISHED rows older than the retention window so the
     * table does not grow unbounded. PENDING/FAILED rows are never purged.
     */
    @Scheduled(cron = "${payment.outbox.purge-cron:0 0 3 * * *}")
    @Transactional
    public void purgePublished() {
        int removed = outboxRepository.deletePublishedBefore(
                LocalDateTime.now().minusDays(retentionDays));
        if (removed > 0) {
            log.info(messageSource.getMessage("payment.outbox.purged",
                    new Object[]{removed, retentionDays}, LocaleContextHolder.getLocale()));
        }
    }
}
