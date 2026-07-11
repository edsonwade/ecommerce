package code.with.vanilson.productservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByCorrelationIdAndStatus(
            String correlationId, InventoryReservation.ReservationStatus status);

    /**
     * All reservation rows for a saga, regardless of status. Used as the
     * idempotency record by the reservation consumer: any row for a
     * correlationId means the order.requested event was already processed
     * (the rows are written in the same transaction as the stock deduction),
     * so a redelivered event must not deduct stock again. Served by the
     * existing (correlation_id, status) index — correlation_id is its prefix.
     */
    List<InventoryReservation> findByCorrelationId(String correlationId);
}
