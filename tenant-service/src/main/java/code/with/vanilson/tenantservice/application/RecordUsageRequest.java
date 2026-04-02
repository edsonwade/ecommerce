package code.with.vanilson.tenantservice.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * RecordUsageRequest — Application Layer DTO
 * Received on POST /api/v1/tenants/{tenantId}/usage
 * <p>
 * Records a usage increment for a specific metric.
 *
 * @author vamuhong
 * @version 4.0
 */
public record RecordUsageRequest(

        @NotBlank(message = "{usage.metric.name.required}")
        String metricName,

        @Min(value = 1, message = "{usage.metric.delta.min}")
        long delta
) {}
