package code.with.vanilson.tenantcontext;

import code.with.vanilson.tenantcontext.exception.MissingTenantException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * TenantInterceptor — HTTP Interceptor
 * <p>
 * Extracts the {@code X-Tenant-ID} header from every incoming HTTP request
 * and stores it in {@link TenantContext} for the duration of the request.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>{@code preHandle}  → extracts header, sets TenantContext</li>
 *   <li>Controller + Service execute with tenant context available</li>
 *   <li>{@code afterCompletion} → clears TenantContext to prevent leaks</li>
 * </ol>
 * <p>
 * Excluded paths (health, actuator, swagger) are configured in
 * {@link TenantContextAutoConfiguration}.
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    private final boolean tenantRequired;

    /**
     * @param tenantRequired if {@code true}, requests without X-Tenant-ID
     *                       will be rejected with {@link MissingTenantException}.
     *                       If {@code false}, the request proceeds without tenant context.
     */
    public TenantInterceptor(boolean tenantRequired) {
        this.tenantRequired = tenantRequired;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String tenantId = request.getHeader(TenantContext.TENANT_HEADER);

        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setCurrentTenantId(tenantId.trim());
            log.debug("Tenant context set: tenantId=[{}] path=[{}]",
                    tenantId, request.getRequestURI());
        } else if (tenantRequired) {
            log.warn("Missing X-Tenant-ID header on path=[{}]", request.getRequestURI());
            throw new MissingTenantException();
        } else {
            log.trace("No X-Tenant-ID header — tenant context not required for path=[{}]",
                    request.getRequestURI());
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        TenantContext.clear();
    }
}
