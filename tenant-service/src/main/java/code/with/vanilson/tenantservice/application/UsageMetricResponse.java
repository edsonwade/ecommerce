package code.with.vanilson.tenantservice.application;

import code.with.vanilson.tenantservice.domain.TenantUsageMetric;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UsageMetricResponse — Application Layer DTO
 * <p>
 * Returned when querying tenant usage metrics.
 * Exposes only safe fields — never exposes internal DB id (Long).
 *
 * @author vamuhong
 * @version 4.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageMetricResponse(
        String        tenantId,
        String        metricName,
        long          metricValue,
        LocalDate     periodDate,
        LocalDateTime createdAt
) {
    public static UsageMetricResponse from(TenantUsageMetric metric) {
        return new UsageMetricResponse(
                metric.getTenantId(),
                metric.getMetricName(),
                metric.getMetricValue(),
                metric.getPeriodDate(),
                metric.getCreatedAt()
        );
    }
}
