package code.with.vanilson.tenantcontext;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * TenantFeignInterceptor — Feign RequestInterceptor
 * <p>
 * Propagates the current tenant ID from {@link TenantContext} into outgoing
 * Feign client calls by adding the {@code X-Tenant-ID} header.
 * <p>
 * Without this interceptor, inter-service calls would lose tenant context
 * and downstream services would reject the request or return unfiltered data.
 * <p>
 * Registered as a Spring bean in {@link TenantContextAutoConfiguration}.
 * Any Feign client in a service that depends on {@code tenant-context}
 * will automatically propagate the tenant header.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
public class TenantFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (TenantContext.isPresent()) {
            String tenantId = TenantContext.getCurrentTenantId();
            template.header(TenantContext.TENANT_HEADER, tenantId);
            log.debug("Feign propagation: X-Tenant-ID=[{}] → {}",
                    tenantId, template.url());
        } else {
            log.trace("No tenant context — Feign call without X-Tenant-ID: {}",
                    template.url());
        }
        // Forward JWT from the current incoming request to downstream Feign calls
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isBlank()) {
                template.header("Authorization", auth);
                log.debug("Feign propagation: Authorization forwarded → {}", template.url());
            }
        }
    }
}
