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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * TenantFeatureFlag — Domain Entity
 * <p>
 * Per-tenant feature toggle. Enables / disables specific platform features
 * for a tenant without deploying new code.
 * <p>
 * Examples:
 * - "FLASH_SALE_ENABLED"   → tenant can run flash sales
 * - "AI_RECOMMENDATIONS"   → recommendation engine active for this tenant
 * - "MULTI_CURRENCY"       → multi-currency checkout enabled
 * - "ADVANCED_ANALYTICS"   → detailed Grafana dashboards accessible
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
@Table(name = "tenant_feature_flag")
@EntityListeners(AuditingEntityListener.class)
public class TenantFeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "flag_name", nullable = false, length = 100)
    private String flagName;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(length = 500)
    private String description;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;
}
