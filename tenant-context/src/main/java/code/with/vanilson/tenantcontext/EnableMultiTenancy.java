package code.with.vanilson.tenantcontext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * EnableMultiTenancy — Activation Annotation
 * <p>
 * Add this annotation to any Spring Boot application class to activate
 * the full multi-tenancy stack:
 * <ul>
 *   <li>TenantContext (ThreadLocal tenant ID holder)</li>
 *   <li>TenantInterceptor (X-Tenant-ID header extraction)</li>
 *   <li>TenantHibernateFilterActivator (automatic WHERE clause)</li>
 *   <li>TenantFeignInterceptor (inter-service propagation)</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code @SpringBootApplication}
 * {@code @EnableMultiTenancy}
 * public class OrderServiceApplication { }
 * </pre>
 *
 * @author vamuhong
 * @version 4.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TenantContextAutoConfiguration.class)
public @interface EnableMultiTenancy {

    /**
     * If {@code true}, requests without X-Tenant-ID header will be rejected
     * with HTTP 400. If {@code false}, tenant context is optional.
     * <p>
     * Default: {@code true} — SaaS services should always require a tenant.
     */
    boolean required() default true;
}
