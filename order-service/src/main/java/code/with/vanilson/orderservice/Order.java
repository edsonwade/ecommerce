package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.orderLine.OrderLine;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order — Domain Entity (Phase 3 update)
 * <p>
 * Phase 3 additions:
 * - correlationId: UUID that identifies this order across the Kafka saga.
 *   Clients poll GET /orders/{correlationId}/status to track progress.
 * - status: tracks where the order is in the saga state machine.
 * - Both fields are persisted and indexed for fast status queries.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@Table(name = "customer_order")
public class Order {

    @Id
    @GeneratedValue
    @JsonProperty("id")
    private Integer orderId;

    /**
     * Correlation ID — client-facing tracking ID.
     * Unique per order. Clients poll /orders/{correlationId}/status.
     * Different from orderId (internal DB key) — decouples internal ID from API.
     */
    @Column(unique = true, nullable = false, length = 36)
    private String correlationId;

    @Column(unique = true, nullable = false)
    private String reference;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private String customerId;

    /**
     * Saga status — updated by OrderSagaConsumer as events arrive from Kafka.
     * Initial value: REQUESTED (set when event is published).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.REQUESTED;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderLine> orderLines;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime lastModifiedDate;
}
