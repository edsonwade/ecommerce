package code.with.vanilson.tenantservice.infrastructure;

import code.with.vanilson.tenantservice.domain.TenantFeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TenantFeatureFlagRepository — Infrastructure Layer
 *
 * @author vamuhong
 * @version 4.0
 */
@Repository
public interface TenantFeatureFlagRepository extends JpaRepository<TenantFeatureFlag, Long> {

    List<TenantFeatureFlag> findAllByTenantId(String tenantId);

    Optional<TenantFeatureFlag> findByTenantIdAndFlagName(String tenantId, String flagName);

    boolean existsByTenantIdAndFlagName(String tenantId, String flagName);

    @Modifying
    @Query("UPDATE TenantFeatureFlag f SET f.enabled = :enabled WHERE f.tenantId = :tenantId AND f.flagName = :flagName")
    int updateFlagEnabled(@Param("tenantId") String tenantId,
                          @Param("flagName") String flagName,
                          @Param("enabled") boolean enabled);
}
