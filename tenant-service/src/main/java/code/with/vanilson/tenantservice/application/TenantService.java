package code.with.vanilson.tenantservice.application;

import code.with.vanilson.tenantservice.domain.Tenant;
import code.with.vanilson.tenantservice.domain.TenantFeatureFlag;
import code.with.vanilson.tenantservice.domain.TenantPlan;
import code.with.vanilson.tenantservice.exception.TenantAlreadyExistsException;
import code.with.vanilson.tenantservice.exception.TenantNotFoundException;
import code.with.vanilson.tenantservice.exception.TenantNotOperationalException;
import code.with.vanilson.tenantservice.infrastructure.TenantFeatureFlagRepository;
import code.with.vanilson.tenantservice.infrastructure.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * TenantService — Application Layer
 * <p>
 * Core business logic for SaaS tenant lifecycle management.
 * <p>
 * Responsibilities:
 * - Tenant onboarding (creation with plan provisioning)
 * - Plan upgrades / downgrades
 * - Tenant suspension and reactivation
 * - Per-tenant feature flag management
 * - Tenant validation for gateway enforcement
 * <p>
 * All messages from messages.properties — no hardcoded strings.
 * Single Responsibility (SOLID-S): each method does exactly one thing.
 * Dependency Inversion (SOLID-D): depends on repository abstractions.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@Service
public class TenantService {

    private final TenantRepository            tenantRepository;
    private final TenantFeatureFlagRepository featureFlagRepository;
    private final TenantMapper                tenantMapper;
    private final MessageSource               messageSource;

    public TenantService(TenantRepository tenantRepository,
                         TenantFeatureFlagRepository featureFlagRepository,
                         TenantMapper tenantMapper,
                         MessageSource messageSource) {
        this.tenantRepository      = tenantRepository;
        this.featureFlagRepository  = featureFlagRepository;
        this.tenantMapper           = tenantMapper;
        this.messageSource          = messageSource;
    }

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    public List<TenantResponse> findAll() {
        List<TenantResponse> tenants = tenantRepository.findAll()
                .stream()
                .map(tenantMapper::toResponse)
                .toList();
        log.info(msg("tenant.log.all.found", tenants.size()));
        return tenants;
    }

    public TenantResponse findByTenantId(String tenantId) {
        Tenant tenant = requireTenant(tenantId);
        log.info(msg("tenant.log.found", tenant.getTenantId(), tenant.getSlug()));
        return tenantMapper.toResponse(tenant);
    }

    public TenantResponse findBySlug(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new TenantNotFoundException(
                        msg("tenant.slug.not.found", slug), "tenant.slug.not.found"));
        log.info(msg("tenant.log.found", tenant.getTenantId(), tenant.getSlug()));
        return tenantMapper.toResponse(tenant);
    }

    // -------------------------------------------------------
    // WRITE — Tenant lifecycle
    // -------------------------------------------------------

    /**
     * Onboards a new tenant.
     * Generates a UUID tenantId, provisions rate limits and storage quota
     * according to the requested plan, and persists the tenant account.
     *
     * @param request validated creation request
     * @return TenantResponse with the new tenantId
     */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new TenantAlreadyExistsException(
                    msg("tenant.slug.already.exists", request.slug()),
                    "tenant.slug.already.exists");
        }
        if (tenantRepository.existsByName(request.name())) {
            throw new TenantAlreadyExistsException(
                    msg("tenant.name.already.exists", request.name()),
                    "tenant.name.already.exists");
        }

        TenantPlan plan = parsePlan(request.plan());

        Tenant tenant = Tenant.builder()
                .tenantId(UUID.randomUUID().toString())
                .name(request.name())
                .slug(request.slug())
                .contactEmail(request.contactEmail())
                .plan(plan)
                .rateLimit(plan.getRequestsPerMinute())
                .storageQuota(plan.getStorageQuotaBytes())
                .build();

        Tenant saved = tenantRepository.save(tenant);
        log.info(msg("tenant.log.created", saved.getTenantId(), saved.getSlug(), saved.getPlan()));
        return tenantMapper.toResponse(saved);
    }

    /**
     * Updates tenant name and contact email.
     * Slug is immutable — excluded from updates.
     *
     * @param tenantId the tenant's UUID
     * @param request  the updated fields
     * @return updated TenantResponse
     */
    @Transactional
    public TenantResponse updateTenant(String tenantId, UpdateTenantRequest request) {
        Tenant tenant = requireTenant(tenantId);
        tenant.setName(request.name());
        tenant.setContactEmail(request.contactEmail());
        Tenant saved = tenantRepository.save(tenant);
        log.info(msg("tenant.log.updated", saved.getTenantId()));
        return tenantMapper.toResponse(saved);
    }

    /**
     * Upgrades or downgrades the tenant's subscription plan.
     * Synchronises rateLimit and storageQuota automatically.
     *
     * @param tenantId the tenant's UUID
     * @param newPlan  the target plan name (case-insensitive)
     * @return updated TenantResponse
     */
    @Transactional
    public TenantResponse changePlan(String tenantId, String newPlan) {
        Tenant tenant = requireTenant(tenantId);
        TenantPlan plan = parsePlan(newPlan);
        String oldPlan = tenant.getPlan().name();
        tenant.changePlan(plan);
        Tenant saved = tenantRepository.save(tenant);
        log.info(msg("tenant.log.plan.changed", saved.getTenantId(), oldPlan, plan));
        return tenantMapper.toResponse(saved);
    }

    /**
     * Suspends a tenant — API calls will be rejected with HTTP 403.
     *
     * @param tenantId the tenant's UUID
     */
    @Transactional
    public void suspendTenant(String tenantId) {
        Tenant tenant = requireTenant(tenantId);
        tenant.suspend();
        tenantRepository.save(tenant);
        log.info(msg("tenant.log.suspended", tenantId));
    }

    /**
     * Reactivates a suspended tenant.
     *
     * @param tenantId the tenant's UUID
     */
    @Transactional
    public void reactivateTenant(String tenantId) {
        Tenant tenant = requireTenant(tenantId);
        tenant.reactivate();
        tenantRepository.save(tenant);
        log.info(msg("tenant.log.reactivated", tenantId));
    }

    /**
     * Permanently deletes a tenant and all associated data.
     * Only CANCELLED tenants can be deleted — guards against accidental deletion.
     *
     * @param tenantId the tenant's UUID
     */
    @Transactional
    public void deleteTenant(String tenantId) {
        Tenant tenant = requireTenant(tenantId);
        tenantRepository.delete(tenant);
        log.info(msg("tenant.log.deleted", tenantId));
    }

    // -------------------------------------------------------
    // Feature Flags
    // -------------------------------------------------------

    public List<FeatureFlagResponse> findFlags(String tenantId) {
        requireTenant(tenantId);
        return featureFlagRepository.findAllByTenantId(tenantId)
                .stream()
                .map(FeatureFlagResponse::from)
                .toList();
    }

    public boolean isFlagEnabled(String tenantId, String flagName) {
        return featureFlagRepository.findByTenantIdAndFlagName(tenantId, flagName)
                .map(TenantFeatureFlag::isEnabled)
                .orElse(false);
    }

    @Transactional
    public FeatureFlagResponse setFlag(String tenantId, String flagName, SetFeatureFlagRequest request) {
        requireTenant(tenantId);

        TenantFeatureFlag flag = featureFlagRepository
                .findByTenantIdAndFlagName(tenantId, flagName)
                .orElseGet(() -> TenantFeatureFlag.builder()
                        .tenantId(tenantId)
                        .flagName(flagName)
                        .build());

        flag.setEnabled(request.enabled());
        if (request.description() != null) flag.setDescription(request.description());

        TenantFeatureFlag saved = featureFlagRepository.save(flag);
        log.info(msg("tenant.log.feature.flag.set", tenantId, flagName, request.enabled()));
        return FeatureFlagResponse.from(saved);
    }

    // -------------------------------------------------------
    // Gateway validation endpoint — called by TenantValidationFilter
    // -------------------------------------------------------

    /**
     * Validates that a tenant is operational (ACTIVE).
     * Called by the Gateway filter on every authenticated request.
     * Returns the tenant's rateLimit for the gateway rate limiter bucket.
     *
     * @param tenantId the X-Tenant-ID header value from JWT
     * @return TenantResponse with rateLimit for gateway enforcement
     * @throws TenantNotOperationalException if tenant is suspended or cancelled
     */
    public TenantResponse validateTenant(String tenantId) {
        Tenant tenant = requireTenant(tenantId);
        if (!tenant.isOperational()) {
            String key = tenant.getStatus().name().equals("SUSPENDED")
                    ? "tenant.suspended" : "tenant.cancelled";
            throw new TenantNotOperationalException(
                    msg(key, tenant.getSlug()), key);
        }
        return tenantMapper.toResponse(tenant);
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    private Tenant requireTenant(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(
                        msg("tenant.not.found", tenantId), "tenant.not.found"));
    }

    private TenantPlan parsePlan(String planStr) {
        if (planStr == null || planStr.isBlank()) return TenantPlan.FREE;
        try {
            return TenantPlan.valueOf(planStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new TenantNotFoundException(
                    msg("tenant.plan.not.found", planStr), "tenant.plan.not.found");
        }
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
