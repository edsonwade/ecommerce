package code.with.vanilson.productservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByCorrelationIdAndStatus(
            String correlationId, InventoryReservation.ReservationStatus status);
}
