package code.with.vanilson.tenantservice.infrastructure;

import code.with.vanilson.tenantservice.domain.Tenant;
import code.with.vanilson.tenantservice.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TenantRepository — Infrastructure Layer
 * <p>
 * Interface Segregation (SOLID-I): exposes only what the application layer needs.
 *
 * @author vamuhong
 * @version 4.0
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantId(String tenantId);

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    List<Tenant> findAllByStatus(TenantStatus status);

    @Query("SELECT t FROM Tenant t WHERE t.status = 'ACTIVE' ORDER BY t.createdAt DESC")
    List<Tenant> findAllActive();
}
