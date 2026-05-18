package code.with.vanilson.productservice.kafka;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks stock reserved per order correlationId for saga compensation.
 * When payment fails, the compensation consumer uses these records
 * to restore the exact quantities that were reserved.
 *
 * @author vamuhong
 * @version 4.0
 */
@Entity
@Table(name = "inventory_reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false)
    private Integer productId;

    @Column(nullable = false)
    private Integer reservedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime releasedAt;

    public enum ReservationStatus {
        RESERVED,
        RELEASED
    }
}
