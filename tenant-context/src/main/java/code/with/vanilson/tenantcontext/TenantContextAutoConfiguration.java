package code.with.vanilson.tenantcontext;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * TenantContextAutoConfiguration — Spring Boot Auto-Configuration
 * <p>
 * Wires the multi-tenancy stack:
 * <ol>
 *   <li>Registers {@link TenantInterceptor} on all paths
 *       except health, actuator, swagger, and tenant-service itself</li>
 *   <li>Scans for {@link TenantHibernateFilterActivator}</li>
 *   <li>Conditionally registers {@link TenantFeignInterceptor}
 *       if Feign is on the classpath</li>
 * </ol>
 * <p>
 * Activated either by {@link EnableMultiTenancy} annotation or
 * by Spring Boot auto-configuration via {@code AutoConfiguration.imports}.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@Configuration
public class TenantContextAutoConfiguration implements WebMvcConfigurer {

    /**
     * Paths excluded from tenant header enforcement.
     * These endpoints must be accessible without X-Tenant-ID:
     * - actuator and health checks (infra monitoring)
     * - swagger / OpenAPI docs
     * - tenant-service own endpoints (tenant onboarding happens before tenant exists)
     */
    private static final String[] EXCLUDED_PATHS = {
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api/v1/tenants/**"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantInterceptor(true))
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDED_PATHS);
        log.info("TenantInterceptor registered — excluded paths: {}",
                String.join(", ", EXCLUDED_PATHS));
    }

    /**
     * Hibernate filter configuration loaded ONLY when Hibernate ORM is on the classpath.
     * <p>
     * We key off {@code org.hibernate.Session} (shipped by hibernate-core) rather than
     * {@code jakarta.persistence.EntityManagerFactory}. The latter is present in services
     * that only pull {@code jakarta.persistence-api} (e.g. customer-service uses it for
     * stray annotations but has no JPA starter), leading to activation without a real
     * {@code EntityManager} bean. hibernate-core is only present where spring-boot-starter-data-jpa
     * is declared, so this is a precise "JPA is actually wired" signal.
     * <p>
     * We do NOT use {@code @ConditionalOnBean(EntityManagerFactory.class)} here because
     * condition evaluation on nested configuration classes runs during configuration parsing,
     * before {@code HibernateJpaAutoConfiguration} registers the bean — causing the config
     * to be skipped even in JPA services.
     */
    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "org.hibernate.Session")
    static class HibernateTenantFilterConfig {

        @Bean
        @ConditionalOnMissingBean(TenantHibernateFilterActivator.class)
        public TenantHibernateFilterActivator tenantHibernateFilterActivator(
                jakarta.persistence.EntityManager entityManager) {
            log.info("TenantHibernateFilterActivator registered — Hibernate tenant filter will be activated per request");
            return new TenantHibernateFilterActivator(entityManager);
        }
    }

    /**
     * Feign configuration loaded ONLY when OpenFeign is on the classpath.
     * Isolated in a nested class to prevent NoClassDefFoundError in services
     * that don't use Feign (e.g. notification-service).
     */
    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignTenantPropagationConfig {

        @Bean
        @ConditionalOnMissingBean(TenantFeignInterceptor.class)
        public TenantFeignInterceptor tenantFeignInterceptor() {
            log.info("TenantFeignInterceptor registered — tenant ID will be propagated in Feign calls");
            return new TenantFeignInterceptor();
        }
    }
}
