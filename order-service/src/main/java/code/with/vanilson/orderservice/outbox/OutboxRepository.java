package code.with.vanilson.orderservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OutboxRepository — Infrastructure Layer
 * Queries for events awaiting Kafka publication.
 *
 * @author vamuhong
 * @version 3.0
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * Returns all PENDING events that can still be retried (retryCount < 5).
     * Called by OutboxEventPublisher on a schedule.
     */
    @Query("""
           SELECT o FROM OutboxEvent o
           WHERE o.status = 'PENDING'
             AND o.retryCount < 5
           ORDER BY o.createdAt ASC
           """)
    List<OutboxEvent> findPendingEvents();

    /** Checks if an event with this eventId was already published (idempotency guard). */
    boolean existsByEventId(String eventId);
}
