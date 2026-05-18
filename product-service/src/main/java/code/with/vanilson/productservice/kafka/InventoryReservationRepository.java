package code.with.vanilson.productservice.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByCorrelationIdAndStatus(
            String correlationId, InventoryReservation.ReservationStatus status);
}
