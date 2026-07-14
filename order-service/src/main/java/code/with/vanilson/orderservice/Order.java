package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.orderLine.OrderLine;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantFilterConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order — Domain Entity (Phase 4 update)
 * <p>
 * Phase 4 additions:
 * - tenantId: UUID identifying the SaaS tenant who owns this order.
 *   Propagated via X-Tenant-ID header → TenantContext → persisted here.
 * - Hibernate @FilterDef / @Filter: automatically appends
 *   WHERE tenant_id = :tenantId to all queries when filter is enabled.
 * <p>
 * Phase 3 additions:
 * - correlationId: UUID that identifies this order across the Kafka saga.
 * - status: tracks where the order is in the saga state machine.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@Table(name = "customer_order")
@FilterDef(name = TenantFilterConstants.FILTER_NAME,
           parameters = @ParamDef(name = TenantFilterConstants.PARAM_NAME, type = String.class))
@Filter(name = TenantFilterConstants.FILTER_NAME,
        condition = "tenant_id = :" + TenantFilterConstants.PARAM_NAME)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "customer_order_seq", allocationSize = 50)
    @JsonProperty("id")
    private Integer orderId;

    /** SaaS tenant UUID — set from TenantContext on creation, never updated. */
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

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

    // -------------------------------------------------------
    // Invoice breakdown (nullable — derived on read for legacy orders).
    // total_amount stays the authoritative amount paid (tax-inclusive). subtotal/taxAmount
    // are computed from it + taxRate when absent; discount/promotion are captured by
    // checkout in future and default to none for existing orders.
    // -------------------------------------------------------

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "promotion_code", length = 64)
    private String promotionCode;

    @Column(name = "promotion_amount")
    private BigDecimal promotionAmount;

    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    // -------------------------------------------------------
    // Shipping address (nullable — captured from the checkout form at creation).
    // Legacy orders have none and fall back to the customer snapshot's profile address
    // on read. This is the destination for THIS order, independent of the buyer's profile.
    // -------------------------------------------------------

    @Column(name = "shipping_street", length = 256)
    private String shippingStreet;

    @Column(name = "shipping_house_number", length = 64)
    private String shippingHouseNumber;

    @Column(name = "shipping_zip_code", length = 64)
    private String shippingZipCode;

    @Column(name = "shipping_city", length = 128)
    private String shippingCity;

    @Column(name = "shipping_country", length = 128)
    private String shippingCountry;

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

    /**
     * Fase 5 (fulfillment) — set when a seller/admin marks the order SHIPPED / DELIVERED
     * via the manual status endpoint. Nullable: legacy and not-yet-shipped orders have none.
     */
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderLine> orderLines;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime lastModifiedDate;
}
