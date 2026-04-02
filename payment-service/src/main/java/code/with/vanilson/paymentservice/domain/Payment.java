package code.with.vanilson.paymentservice.domain;

import code.with.vanilson.tenantcontext.TenantFilterConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/**
 * Payment — Domain Entity
 * <p>
 * Represents a payment record. The source of truth for all payment data.
 * <p>
 * KEY CHANGES FROM ORIGINAL:
 * 1. Uses local PaymentMethod enum — no cross-service JAR import.
 * 2. Added idempotencyKey column — prevents double charges on retries.
 *    Unique constraint enforced at DB level: two payment requests for the same
 *    orderReference cannot both be persisted.
 * 3. GenerationType.SEQUENCE replaces GenerationType.AUTO for PostgreSQL compatibility.
 * <p>
 * Clean Architecture: pure domain entity — no HTTP, no Kafka, no Spring Cloud deps.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@FilterDef(
        name = TenantFilterConstants.FILTER_NAME,
        parameters = @ParamDef(name = TenantFilterConstants.PARAM_NAME, type = String.class)
)
@Filter(
        name = TenantFilterConstants.FILTER_NAME,
        condition = "tenant_id = :" + TenantFilterConstants.PARAM_NAME
)
@NoArgsConstructor
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @JsonProperty("id")
    private Integer paymentId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private Integer orderId;

    @Column(nullable = false)
    private String orderReference;

    /**
     * Idempotency key — derived from orderReference.
     * Unique DB constraint prevents double payments for the same order.
     * If the same orderReference arrives twice (retry, network glitch),
     * the second insert fails at DB level and the existing payment is returned.
     */
    @Column(unique = true, nullable = false, length = 255)
    private String idempotencyKey;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime lastModifiedDate;

    /**
     * Phase 4: Tenant isolation — every payment belongs to exactly one tenant.
     * Populated automatically from TenantContext; filtered via Hibernate @Filter.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;
}
