package code.with.vanilson.tenantservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * TenantUsageMetric — Domain Entity
 * <p>
 * Tracks API usage and business metrics per tenant per day.
 * Used for billing, rate limiting enforcement, and SaaS analytics.
 * <p>
 * Each row represents one metric for one tenant on one day.
 * The unique constraint (tenant_id, metric_name, period_date) prevents duplicates.
 * <p>
 * Common metrics:
 * - "api.calls"        → total API calls made by this tenant today
 * - "orders.created"   → orders placed by this tenant today
 * - "products.created" → products added by this tenant today
 * - "storage.bytes"    → current storage consumption in bytes
 *
 * @author vamuhong
 * @version 4.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "tenant_usage_metric")
@EntityListeners(AuditingEntityListener.class)
public class TenantUsageMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(name = "metric_value", nullable = false)
    @Builder.Default
    private long metricValue = 0L;

    @Column(name = "period_date", nullable = false)
    @Builder.Default
    private LocalDate periodDate = LocalDate.now();

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    // -------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------

    /** Increments the metric value by the given delta. */
    public void increment(long delta) {
        this.metricValue += delta;
    }

    /** Increments the metric value by 1. */
    public void increment() {
        increment(1L);
    }

    /** Resets the metric value to zero (used for daily rollover). */
    public void reset() {
        this.metricValue = 0L;
    }
}
