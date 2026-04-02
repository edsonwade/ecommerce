package code.with.vanilson.tenantservice.domain;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Tenant — Domain Entity
 * <p>
 * Represents a SaaS tenant account. Each tenant maps to a company or individual
 * using the eCommerce platform. Tenants are isolated at the data layer via tenant_id
 * column in every service database (Hibernate filters enforce this).
 * <p>
 * Clean Architecture: pure domain entity — no HTTP, Kafka, or cloud deps.
 * <p>
 * Key fields:
 * - tenantId:    UUID used across all services to identify the tenant (propagated in JWT)
 * - slug:        URL-friendly identifier (e.g. "acme-corp") — immutable after creation
 * - plan:        determines rate limits and storage quota
 * - status:      ACTIVE / SUSPENDED / CANCELLED — enforced by Gateway
 * - rateLimit:   requests per minute allowed by this tenant's plan
 * - storageQuota: max storage bytes (-1 = unlimited)
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
@Table(name = "tenant")
@EntityListeners(AuditingEntityListener.class)
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 36)
    private String tenantId;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    /**
     * URL-friendly unique identifier. Immutable after creation.
     * Format: lowercase letters, digits and hyphens only.
     * Example: "acme-corp", "my-shop-123"
     */
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TenantPlan plan = TenantPlan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    /** Rate limit in requests per minute — derived from plan, stored for fast enforcement. */
    @Column(nullable = false)
    @Builder.Default
    private int rateLimit = TenantPlan.FREE.getRequestsPerMinute();

    /** Storage quota in bytes — derived from plan, stored for fast enforcement. */
    @Column(nullable = false)
    @Builder.Default
    private long storageQuota = TenantPlan.FREE.getStorageQuotaBytes();

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    // -------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------

    /** Returns true if this tenant is allowed to make API calls. */
    public boolean isOperational() {
        return status == TenantStatus.ACTIVE;
    }

    /** Updates the plan and synchronises rateLimit and storageQuota accordingly. */
    public void changePlan(TenantPlan newPlan) {
        this.plan         = newPlan;
        this.rateLimit    = newPlan.getRequestsPerMinute();
        this.storageQuota = newPlan.getStorageQuotaBytes();
    }

    public void suspend()    { this.status = TenantStatus.SUSPENDED; }
    public void reactivate() { this.status = TenantStatus.ACTIVE;    }
    public void cancel()     { this.status = TenantStatus.CANCELLED;  }
}
