package code.with.vanilson.tenantservice.config;

import code.with.vanilson.tenantservice.domain.Tenant;
import code.with.vanilson.tenantservice.domain.TenantPlan;
import code.with.vanilson.tenantservice.domain.TenantStatus;
import code.with.vanilson.tenantservice.infrastructure.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the built-in "default" tenant exists and is ACTIVE at startup.
 * Regular users who register without specifying a tenant are assigned tenantId="default".
 * Without this record the gateway TenantValidationFilter returns 404 for all their requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTenantBootstrapRunner implements ApplicationRunner {

    private static final String DEFAULT_TENANT_ID = "default";

    private final TenantRepository tenantRepository;

    @Override
    public void run(ApplicationArguments args) {
        tenantRepository.findByTenantId(DEFAULT_TENANT_ID).ifPresentOrElse(
                existing -> {
                    if (existing.getStatus() != TenantStatus.ACTIVE) {
                        existing.reactivate();
                        tenantRepository.save(existing);
                        log.info("[DefaultTenantBootstrap] Reactivated '{}' tenant (was {})",
                                DEFAULT_TENANT_ID, existing.getStatus());
                    } else {
                        log.info("[DefaultTenantBootstrap] '{}' tenant already ACTIVE — skipping",
                                DEFAULT_TENANT_ID);
                    }
                },
                () -> {
                    Tenant def = Tenant.builder()
                            .tenantId(DEFAULT_TENANT_ID)
                            .name("Default Platform Tenant")
                            .slug("default")
                            .contactEmail("platform@obsidian.market")
                            .plan(TenantPlan.FREE)
                            .rateLimit(TenantPlan.FREE.getRequestsPerMinute())
                            .storageQuota(TenantPlan.FREE.getStorageQuotaBytes())
                            .build();
                    tenantRepository.save(def);
                    log.info("[DefaultTenantBootstrap] Created '{}' tenant", DEFAULT_TENANT_ID);
                }
        );
    }
}
