package code.with.vanilson.tenantservice.unit;

import code.with.vanilson.tenantservice.application.CreateTenantRequest;
import code.with.vanilson.tenantservice.application.FeatureFlagResponse;
import code.with.vanilson.tenantservice.application.SetFeatureFlagRequest;
import code.with.vanilson.tenantservice.application.TenantMapper;
import code.with.vanilson.tenantservice.application.TenantResponse;
import code.with.vanilson.tenantservice.application.TenantService;
import code.with.vanilson.tenantservice.application.UpdateTenantRequest;
import code.with.vanilson.tenantservice.domain.Tenant;
import code.with.vanilson.tenantservice.domain.TenantFeatureFlag;
import code.with.vanilson.tenantservice.domain.TenantPlan;
import code.with.vanilson.tenantservice.domain.TenantStatus;
import code.with.vanilson.tenantservice.exception.TenantAlreadyExistsException;
import code.with.vanilson.tenantservice.exception.TenantNotFoundException;
import code.with.vanilson.tenantservice.exception.TenantNotOperationalException;
import code.with.vanilson.tenantservice.infrastructure.TenantFeatureFlagRepository;
import code.with.vanilson.tenantservice.infrastructure.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantServiceTest — Unit Tests
 * <p>
 * Framework: JUnit 5 + Mockito + AssertJ.
 * @Nested groups related scenarios for readability.
 * MessageSource returns the key itself — no .properties dependency in tests.
 *
 * @author vamuhong
 * @version 4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService — Unit Tests")
class TenantServiceTest {

    @Mock private TenantRepository            tenantRepository;
    @Mock private TenantFeatureFlagRepository featureFlagRepository;
    @Mock private TenantMapper                tenantMapper;
    @Mock private MessageSource               messageSource;

    @InjectMocks
    private TenantService tenantService;

    private Tenant activeTenant;
    private TenantResponse activeTenantResponse;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        activeTenant = Tenant.builder()
                .id(1L)
                .tenantId("tenant-001")
                .name("Acme Corp")
                .slug("acme-corp")
                .contactEmail("admin@acme.com")
                .plan(TenantPlan.FREE)
                .status(TenantStatus.ACTIVE)
                .rateLimit(100)
                .storageQuota(1_073_741_824L)
                .createdAt(LocalDateTime.now())
                .build();

        activeTenantResponse = new TenantResponse(
                "tenant-001", "Acme Corp", "acme-corp",
                "admin@acme.com", "FREE", "ACTIVE",
                100, 1_073_741_824L, LocalDateTime.now(), null);
    }

    // -------------------------------------------------------
    @Nested @DisplayName("createTenant")
    class CreateTenant {

        @Test @DisplayName("should create tenant and return TenantResponse on valid request")
        void shouldCreateSuccessfully() {
            CreateTenantRequest req = new CreateTenantRequest("Acme Corp", "acme-corp", "admin@acme.com", "FREE");
            when(tenantRepository.existsBySlug("acme-corp")).thenReturn(false);
            when(tenantRepository.existsByName("Acme Corp")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenReturn(activeTenant);
            when(tenantMapper.toResponse(activeTenant)).thenReturn(activeTenantResponse);

            TenantResponse result = tenantService.createTenant(req);

            assertThat(result).isNotNull();
            assertThat(result.slug()).isEqualTo("acme-corp");
            assertThat(result.plan()).isEqualTo("FREE");
            verify(tenantRepository, times(1)).save(any(Tenant.class));
        }

        @Test @DisplayName("should set rateLimit from plan on new tenant")
        void shouldSetRateLimitFromPlan() {
            CreateTenantRequest req = new CreateTenantRequest("Beta Corp", "beta-corp", "admin@beta.com", "STARTER");
            when(tenantRepository.existsBySlug("beta-corp")).thenReturn(false);
            when(tenantRepository.existsByName("Beta Corp")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenReturn(activeTenant);
            when(tenantMapper.toResponse(any())).thenReturn(activeTenantResponse);

            tenantService.createTenant(req);

            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getRateLimit())
                    .as("STARTER plan must set rateLimit to 1000")
                    .isEqualTo(TenantPlan.STARTER.getRequestsPerMinute());
        }

        @Test @DisplayName("should throw TenantAlreadyExistsException when slug is taken")
        void shouldThrowWhenSlugTaken() {
            CreateTenantRequest req = new CreateTenantRequest("Acme Corp", "acme-corp", "admin@acme.com", null);
            when(tenantRepository.existsBySlug("acme-corp")).thenReturn(true);

            assertThatThrownBy(() -> tenantService.createTenant(req))
                    .isInstanceOf(TenantAlreadyExistsException.class)
                    .hasMessageContaining("tenant.slug.already.exists");
            verify(tenantRepository, never()).save(any());
        }

        @Test @DisplayName("should throw TenantAlreadyExistsException when name is taken")
        void shouldThrowWhenNameTaken() {
            CreateTenantRequest req = new CreateTenantRequest("Acme Corp", "acme-new", "new@acme.com", null);
            when(tenantRepository.existsBySlug("acme-new")).thenReturn(false);
            when(tenantRepository.existsByName("Acme Corp")).thenReturn(true);

            assertThatThrownBy(() -> tenantService.createTenant(req))
                    .isInstanceOf(TenantAlreadyExistsException.class)
                    .hasMessageContaining("tenant.name.already.exists");
        }

        @Test @DisplayName("should default to FREE plan when plan is null")
        void shouldDefaultToFreePlan() {
            CreateTenantRequest req = new CreateTenantRequest("Gamma Co", "gamma-co", "info@gamma.com", null);
            when(tenantRepository.existsBySlug("gamma-co")).thenReturn(false);
            when(tenantRepository.existsByName("Gamma Co")).thenReturn(false);
            when(tenantRepository.save(any())).thenReturn(activeTenant);
            when(tenantMapper.toResponse(any())).thenReturn(activeTenantResponse);

            tenantService.createTenant(req);

            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getPlan()).isEqualTo(TenantPlan.FREE);
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("updateTenant")
    class UpdateTenant {

        @Test @DisplayName("should update name and contactEmail")
        void shouldUpdateSuccessfully() {
            UpdateTenantRequest req = new UpdateTenantRequest("Acme Corp Updated", "updated@acme.com");
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));
            when(tenantRepository.save(any())).thenReturn(activeTenant);
            when(tenantMapper.toResponse(any())).thenReturn(activeTenantResponse);

            tenantService.updateTenant("tenant-001", req);

            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Acme Corp Updated");
            assertThat(captor.getValue().getContactEmail()).isEqualTo("updated@acme.com");
        }

        @Test @DisplayName("should throw TenantNotFoundException when tenant does not exist")
        void shouldThrowWhenNotFound() {
            when(tenantRepository.findByTenantId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tenantService.updateTenant("ghost",
                    new UpdateTenantRequest("X", "x@x.com")))
                    .isInstanceOf(TenantNotFoundException.class)
                    .hasMessageContaining("tenant.not.found");
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("changePlan")
    class ChangePlan {

        @Test @DisplayName("should upgrade plan and sync rateLimit")
        void shouldUpgradePlanAndSyncRateLimit() {
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));
            when(tenantRepository.save(any())).thenReturn(activeTenant);
            when(tenantMapper.toResponse(any())).thenReturn(activeTenantResponse);

            tenantService.changePlan("tenant-001", "GROWTH");

            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getPlan()).isEqualTo(TenantPlan.GROWTH);
            assertThat(captor.getValue().getRateLimit())
                    .isEqualTo(TenantPlan.GROWTH.getRequestsPerMinute());
        }

        @Test @DisplayName("should throw TenantNotFoundException for invalid plan name")
        void shouldThrowForInvalidPlan() {
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));

            assertThatThrownBy(() -> tenantService.changePlan("tenant-001", "DIAMOND"))
                    .isInstanceOf(TenantNotFoundException.class)
                    .hasMessageContaining("tenant.plan.not.found");
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("suspendTenant / reactivateTenant")
    class StatusManagement {

        @Test @DisplayName("should set status to SUSPENDED")
        void shouldSuspend() {
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));

            tenantService.suspendTenant("tenant-001");

            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        }

        @Test @DisplayName("should set status to ACTIVE on reactivation")
        void shouldReactivate() {
            activeTenant.suspend();
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));

            tenantService.reactivateTenant("tenant-001");

            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("validateTenant")
    class ValidateTenant {

        @Test @DisplayName("should return TenantResponse for ACTIVE tenant")
        void shouldPassForActiveTenant() {
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));
            when(tenantMapper.toResponse(activeTenant)).thenReturn(activeTenantResponse);

            TenantResponse result = tenantService.validateTenant("tenant-001");

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("ACTIVE");
        }

        @Test @DisplayName("should throw TenantNotOperationalException for SUSPENDED tenant")
        void shouldThrowForSuspendedTenant() {
            activeTenant.suspend();
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));

            assertThatThrownBy(() -> tenantService.validateTenant("tenant-001"))
                    .isInstanceOf(TenantNotOperationalException.class)
                    .hasMessageContaining("tenant.suspended");
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Feature Flags")
    class FeatureFlags {

        @Test @DisplayName("should create a new feature flag when it does not exist")
        void shouldCreateNewFlag() {
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));
            when(featureFlagRepository.findByTenantIdAndFlagName("tenant-001", "FLASH_SALE"))
                    .thenReturn(Optional.empty());
            TenantFeatureFlag saved = TenantFeatureFlag.builder()
                    .id(1L).tenantId("tenant-001").flagName("FLASH_SALE").enabled(true).build();
            when(featureFlagRepository.save(any())).thenReturn(saved);

            FeatureFlagResponse result = tenantService.setFlag(
                    "tenant-001", "FLASH_SALE", new SetFeatureFlagRequest(true, "Enable flash sales"));

            assertThat(result.flagName()).isEqualTo("FLASH_SALE");
            assertThat(result.enabled()).isTrue();
            verify(featureFlagRepository, times(1)).save(any());
        }

        @Test @DisplayName("should update existing flag without duplicating")
        void shouldUpdateExistingFlag() {
            TenantFeatureFlag existing = TenantFeatureFlag.builder()
                    .id(1L).tenantId("tenant-001").flagName("FLASH_SALE").enabled(false).build();
            when(tenantRepository.findByTenantId("tenant-001")).thenReturn(Optional.of(activeTenant));
            when(featureFlagRepository.findByTenantIdAndFlagName("tenant-001", "FLASH_SALE"))
                    .thenReturn(Optional.of(existing));
            when(featureFlagRepository.save(any())).thenReturn(existing);

            tenantService.setFlag("tenant-001", "FLASH_SALE", new SetFeatureFlagRequest(true, null));

            ArgumentCaptor<TenantFeatureFlag> captor = ArgumentCaptor.forClass(TenantFeatureFlag.class);
            verify(featureFlagRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(1L);
            assertThat(captor.getValue().isEnabled()).isTrue();
        }

        @Test @DisplayName("isFlagEnabled should return false when flag not found")
        void shouldReturnFalseWhenFlagNotFound() {
            when(featureFlagRepository.findByTenantIdAndFlagName("tenant-001", "UNKNOWN"))
                    .thenReturn(Optional.empty());

            boolean result = tenantService.isFlagEnabled("tenant-001", "UNKNOWN");

            assertThat(result).isFalse();
        }
    }
}
