package code.with.vanilson.tenantservice.presentation;

import code.with.vanilson.tenantservice.application.CreateTenantRequest;
import code.with.vanilson.tenantservice.application.FeatureFlagResponse;
import code.with.vanilson.tenantservice.application.RecordUsageRequest;
import code.with.vanilson.tenantservice.application.SetFeatureFlagRequest;
import code.with.vanilson.tenantservice.application.TenantResponse;
import code.with.vanilson.tenantservice.application.TenantService;
import code.with.vanilson.tenantservice.application.TenantUsageMetricService;
import code.with.vanilson.tenantservice.application.UpdateTenantRequest;
import code.with.vanilson.tenantservice.application.UsageMetricResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * TenantController — Presentation Layer
 * <p>
 * REST controller for SaaS tenant management.
 * Single Responsibility (SOLID-S): HTTP concerns only — all logic delegated to services.
 * <p>
 * REST design:
 * GET    /api/v1/tenants                                → list all tenants
 * GET    /api/v1/tenants/{tenantId}                     → get by tenantId
 * GET    /api/v1/tenants/by-slug/{slug}                 → get by slug
 * GET    /api/v1/tenants/{tenantId}/validate            → gateway validation
 * POST   /api/v1/tenants                                → onboard new tenant (201)
 * PUT    /api/v1/tenants/{tenantId}                     → update name + email
 * PATCH  /api/v1/tenants/{tenantId}/plan                → change plan
 * PATCH  /api/v1/tenants/{tenantId}/suspend             → suspend tenant
 * PATCH  /api/v1/tenants/{tenantId}/reactivate          → reactivate tenant
 * DELETE /api/v1/tenants/{tenantId}                     → delete tenant (204)
 * GET    /api/v1/tenants/{tenantId}/flags               → list feature flags
 * PUT    /api/v1/tenants/{tenantId}/flags/{name}        → set feature flag
 * GET    /api/v1/tenants/{tenantId}/flags/{name}/status → check flag
 * POST   /api/v1/tenants/{tenantId}/usage               → record usage metric
 * GET    /api/v1/tenants/{tenantId}/usage               → query usage by date
 * GET    /api/v1/tenants/{tenantId}/usage/range         → query usage by date range
 * GET    /api/v1/tenants/{tenantId}/usage/sum           → sum metric by date range
 *
 * @author vamuhong
 * @version 4.0
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant API", description = "SaaS tenant lifecycle, feature flags and usage metering")
public class TenantController {

    private final TenantService             tenantService;
    private final TenantUsageMetricService  usageMetricService;

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    @Operation(summary = "List all tenants")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<TenantResponse>> findAll() {
        return ResponseEntity.ok(tenantService.findAll());
    }

    @Operation(summary = "Get tenant by tenantId (UUID)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant found"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> findByTenantId(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantService.findByTenantId(tenantId));
    }

    @Operation(summary = "Get tenant by slug")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant found"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<TenantResponse> findBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(tenantService.findBySlug(slug));
    }

    @Operation(summary = "Validate tenant (used by Gateway)",
               description = "Returns tenant details including rateLimit. Called by the Gateway TenantValidationFilter.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant is ACTIVE"),
        @ApiResponse(responseCode = "403", description = "Tenant is SUSPENDED or CANCELLED"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @GetMapping("/{tenantId}/validate")
    public ResponseEntity<TenantResponse> validate(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantService.validateTenant(tenantId));
    }

    // -------------------------------------------------------
    // WRITE — Lifecycle
    // -------------------------------------------------------

    @Operation(summary = "Onboard a new tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tenant created"),
        @ApiResponse(responseCode = "409", description = "Slug or name already taken"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TenantResponse> create(@RequestBody @Valid CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
    }

    @Operation(summary = "Update tenant name and contact email")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant updated"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> update(@PathVariable String tenantId,
                                                  @RequestBody @Valid UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(tenantId, request));
    }

    @Operation(summary = "Change tenant subscription plan",
               description = "Valid plans: FREE, STARTER, GROWTH, ENTERPRISE")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plan changed"),
        @ApiResponse(responseCode = "404", description = "Tenant or plan not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{tenantId}/plan")
    public ResponseEntity<TenantResponse> changePlan(@PathVariable String tenantId,
                                                      @RequestParam String plan) {
        return ResponseEntity.ok(tenantService.changePlan(tenantId, plan));
    }

    @Operation(summary = "Suspend tenant — API calls will be rejected")
    @ApiResponse(responseCode = "204", description = "Tenant suspended")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{tenantId}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable String tenantId) {
        tenantService.suspendTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate a suspended tenant")
    @ApiResponse(responseCode = "204", description = "Tenant reactivated")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{tenantId}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable String tenantId) {
        tenantService.reactivateTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Permanently delete a tenant")
    @ApiResponse(responseCode = "204", description = "Tenant deleted")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> delete(@PathVariable String tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------
    // Feature Flags
    // -------------------------------------------------------

    @Operation(summary = "List all feature flags for a tenant")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{tenantId}/flags")
    public ResponseEntity<List<FeatureFlagResponse>> listFlags(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantService.findFlags(tenantId));
    }

    @Operation(summary = "Enable or disable a feature flag for a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Flag updated"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{tenantId}/flags/{flagName}")
    public ResponseEntity<FeatureFlagResponse> setFlag(@PathVariable String tenantId,
                                                        @PathVariable String flagName,
                                                        @RequestBody SetFeatureFlagRequest request) {
        return ResponseEntity.ok(tenantService.setFlag(tenantId, flagName, request));
    }

    @Operation(summary = "Check if a specific feature flag is enabled")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{tenantId}/flags/{flagName}/status")
    public ResponseEntity<Boolean> isFlagEnabled(@PathVariable String tenantId,
                                                  @PathVariable String flagName) {
        return ResponseEntity.ok(tenantService.isFlagEnabled(tenantId, flagName));
    }

    // -------------------------------------------------------
    // Usage Metrics
    // -------------------------------------------------------

    @Operation(summary = "Record a usage metric increment",
               description = "Atomically increments a metric for the tenant on today's date. Creates the metric if it does not exist.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usage recorded"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{tenantId}/usage")
    public ResponseEntity<UsageMetricResponse> recordUsage(@PathVariable String tenantId,
                                                            @RequestBody @Valid RecordUsageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(usageMetricService.recordUsage(tenantId, request.metricName(), request.delta()));
    }

    @Operation(summary = "Query usage metrics by date",
               description = "Returns all usage metrics for a tenant on a specific date. Defaults to today.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{tenantId}/usage")
    public ResponseEntity<List<UsageMetricResponse>> getUsageByDate(
            @PathVariable String tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(usageMetricService.findByDate(tenantId, date));
    }

    @Operation(summary = "Query usage metrics by date range",
               description = "Returns all usage metrics for a tenant between two dates (inclusive).")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{tenantId}/usage/range")
    public ResponseEntity<List<UsageMetricResponse>> getUsageByDateRange(
            @PathVariable String tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(usageMetricService.findByDateRange(tenantId, startDate, endDate));
    }

    @Operation(summary = "Sum a specific metric over a date range",
               description = "Returns the total sum of a metric for billing or analytics. E.g. total API calls this month.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{tenantId}/usage/sum")
    public ResponseEntity<Long> sumMetric(
            @PathVariable String tenantId,
            @RequestParam String metricName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(usageMetricService.sumMetric(tenantId, metricName, startDate, endDate));
    }
}
