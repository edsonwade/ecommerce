package code.with.vanilson.paymentservice.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.QueryHint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PaymentOutboxRepository — Infrastructure Layer (Transactional Outbox, Fase 6.1).
 * Queries the {@code payment_outbox_event} table for the scheduled publisher.
 *
 * @author vamuhong
 * @version 1.0
 */
@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, String> {

    /**
     * Returns a bounded, FIFO batch of PENDING events still eligible for retry
     * (retryCount &lt; 5), oldest first. {@code Pageable} caps the batch size so a
     * backlog is drained across ticks rather than in one unbounded fetch.
     * <p>
     * {@code PESSIMISTIC_WRITE} + {@code SKIP_LOCKED} (lock.timeout = -2) means that
     * if payment-service is ever scaled to multiple instances, each publisher tick
     * claims a disjoint set of rows instead of two instances publishing the same
     * event. Harmless with the current single-instance premise; future-proof.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
           SELECT o FROM PaymentOutboxEvent o
           WHERE o.status = 'PENDING'
             AND o.retryCount < 5
           ORDER BY o.createdAt ASC
           """)
    List<PaymentOutboxEvent> findPendingBatch(Pageable pageable);

    /** Count of PENDING events for the queue-depth gauge. */
    @Query("""
           SELECT COUNT(o) FROM PaymentOutboxEvent o
           WHERE o.status = 'PENDING'
             AND o.retryCount < 5
           """)
    long countPending();

    /**
     * Retention purge — deletes PUBLISHED rows older than the cutoff so the table
     * does not grow unbounded. Called on a schedule by the publisher.
     */
    @Modifying
    @Query("""
           DELETE FROM PaymentOutboxEvent o
           WHERE o.status = 'PUBLISHED'
             AND o.publishedAt < :cutoff
           """)
    int deletePublishedBefore(@Param("cutoff") LocalDateTime cutoff);
}
