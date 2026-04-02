package code.with.vanilson.tenantservice.application;

import code.with.vanilson.tenantservice.domain.TenantUsageMetric;
import code.with.vanilson.tenantservice.exception.TenantNotFoundException;
import code.with.vanilson.tenantservice.infrastructure.TenantRepository;
import code.with.vanilson.tenantservice.infrastructure.TenantUsageMetricRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * TenantUsageMetricService — Application Layer
 * <p>
 * Manages API usage tracking and billing metrics per tenant.
 * Separated from TenantService to respect Single Responsibility (SOLID-S):
 * TenantService handles tenant lifecycle; this service handles usage metering.
 * <p>
 * Responsibilities:
 * - Record daily usage increments (api.calls, orders.created, etc.)
 * - Query usage for a specific date or date range
 * - Sum usage for billing periods
 * <p>
 * All messages from messages.properties — no hardcoded strings.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@Service
public class TenantUsageMetricService {

    private final TenantUsageMetricRepository usageMetricRepository;
    private final TenantRepository            tenantRepository;
    private final MessageSource               messageSource;

    public TenantUsageMetricService(TenantUsageMetricRepository usageMetricRepository,
                                    TenantRepository tenantRepository,
                                    MessageSource messageSource) {
        this.usageMetricRepository = usageMetricRepository;
        this.tenantRepository      = tenantRepository;
        this.messageSource         = messageSource;
    }

    // -------------------------------------------------------
    // RECORD — Upsert usage metric for today
    // -------------------------------------------------------

    /**
     * Records a usage increment for the given tenant and metric.
     * If a record already exists for today, increments the value atomically.
     * Otherwise, creates a new record with the given delta.
     *
     * @param tenantId   the tenant's UUID
     * @param metricName the metric key (e.g. "api.calls", "orders.created")
     * @param delta      the value to add (must be positive)
     * @return UsageMetricResponse with the updated metric
     */
    @Transactional
    public UsageMetricResponse recordUsage(String tenantId, String metricName, long delta) {
        requireTenantExists(tenantId);
        LocalDate today = LocalDate.now();

        int updated = usageMetricRepository.incrementMetric(tenantId, metricName, today, delta);

        TenantUsageMetric metric;
        if (updated == 0) {
            metric = TenantUsageMetric.builder()
                    .tenantId(tenantId)
                    .metricName(metricName)
                    .metricValue(delta)
                    .periodDate(today)
                    .build();
            metric = usageMetricRepository.save(metric);
        } else {
            metric = usageMetricRepository
                    .findByTenantIdAndMetricNameAndPeriodDate(tenantId, metricName, today)
                    .orElseThrow();
        }

        log.info(msg("tenant.log.usage.recorded", tenantId, metricName, metric.getMetricValue()));
        return UsageMetricResponse.from(metric);
    }

    // -------------------------------------------------------
    // QUERY — Read usage metrics
    // -------------------------------------------------------

    /**
     * Returns all usage metrics for a tenant on a specific date.
     *
     * @param tenantId the tenant's UUID
     * @param date     the date to query (defaults to today if null)
     * @return list of UsageMetricResponse for the given date
     */
    public List<UsageMetricResponse> findByDate(String tenantId, LocalDate date) {
        requireTenantExists(tenantId);
        LocalDate queryDate = date != null ? date : LocalDate.now();
        return usageMetricRepository.findAllByTenantIdAndPeriodDate(tenantId, queryDate)
                .stream()
                .map(UsageMetricResponse::from)
                .toList();
    }

    /**
     * Returns all usage metrics for a tenant within a date range (inclusive).
     *
     * @param tenantId  the tenant's UUID
     * @param startDate range start (inclusive)
     * @param endDate   range end (inclusive)
     * @return list of UsageMetricResponse ordered by date ascending
     */
    public List<UsageMetricResponse> findByDateRange(String tenantId,
                                                      LocalDate startDate,
                                                      LocalDate endDate) {
        requireTenantExists(tenantId);
        return usageMetricRepository.findByTenantIdAndDateRange(tenantId, startDate, endDate)
                .stream()
                .map(UsageMetricResponse::from)
                .toList();
    }

    /**
     * Sums a specific metric across a date range.
     * Used for billing: "total API calls this month".
     *
     * @param tenantId   the tenant's UUID
     * @param metricName the metric key
     * @param startDate  range start (inclusive)
     * @param endDate    range end (inclusive)
     * @return the total sum as a long
     */
    public long sumMetric(String tenantId, String metricName,
                          LocalDate startDate, LocalDate endDate) {
        requireTenantExists(tenantId);
        return usageMetricRepository.sumMetricByDateRange(tenantId, metricName, startDate, endDate);
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    private void requireTenantExists(String tenantId) {
        if (!tenantRepository.findByTenantId(tenantId).isPresent()) {
            throw new TenantNotFoundException(
                    msg("tenant.not.found", tenantId), "tenant.not.found");
        }
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
