package code.with.vanilson.tenantservice.infrastructure;

import code.with.vanilson.tenantservice.domain.TenantUsageMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * TenantUsageMetricRepository — Infrastructure Layer
 * <p>
 * Provides CRUD and custom queries for daily usage metrics per tenant.
 * Interface Segregation (SOLID-I): exposes only what the application layer needs.
 *
 * @author vamuhong
 * @version 4.0
 */
@Repository
public interface TenantUsageMetricRepository extends JpaRepository<TenantUsageMetric, Long> {

    /**
     * Finds a specific metric for a tenant on a specific date.
     * Used for upsert logic (increment existing or create new).
     */
    Optional<TenantUsageMetric> findByTenantIdAndMetricNameAndPeriodDate(
            String tenantId, String metricName, LocalDate periodDate);

    /**
     * Returns all metrics for a tenant on a specific date.
     * Used for daily usage dashboards.
     */
    List<TenantUsageMetric> findAllByTenantIdAndPeriodDate(
            String tenantId, LocalDate periodDate);

    /**
     * Returns all metrics for a tenant within a date range (inclusive).
     * Used for billing summaries and trend analysis.
     */
    @Query("SELECT m FROM TenantUsageMetric m " +
           "WHERE m.tenantId = :tenantId " +
           "AND m.periodDate BETWEEN :startDate AND :endDate " +
           "ORDER BY m.periodDate ASC, m.metricName ASC")
    List<TenantUsageMetric> findByTenantIdAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Atomically increments a metric value.
     * Returns the number of rows affected (0 if metric does not exist yet).
     */
    @Modifying
    @Query("UPDATE TenantUsageMetric m " +
           "SET m.metricValue = m.metricValue + :delta " +
           "WHERE m.tenantId = :tenantId " +
           "AND m.metricName = :metricName " +
           "AND m.periodDate = :periodDate")
    int incrementMetric(@Param("tenantId") String tenantId,
                        @Param("metricName") String metricName,
                        @Param("periodDate") LocalDate periodDate,
                        @Param("delta") long delta);

    /**
     * Sums all values for a specific metric across a date range.
     * Used for billing: total API calls this month, total orders this quarter, etc.
     */
    @Query("SELECT COALESCE(SUM(m.metricValue), 0) FROM TenantUsageMetric m " +
           "WHERE m.tenantId = :tenantId " +
           "AND m.metricName = :metricName " +
           "AND m.periodDate BETWEEN :startDate AND :endDate")
    long sumMetricByDateRange(@Param("tenantId") String tenantId,
                              @Param("metricName") String metricName,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate);
}
