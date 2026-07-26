package code.with.vanilson.productservice.review;

import code.with.vanilson.tenantcontext.internal.InternalTokenRequestInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * OrderClientConfig — per-client Feign configuration for {@link OrderClient} (F7).
 * <p>
 * Deliberately NOT annotated {@code @Configuration} / component-scanned: it is referenced only via
 * {@code @FeignClient(configuration = OrderClientConfig.class)}, so its interceptor applies to the
 * order client alone and never becomes a global default. That matters more than it looks — a globally
 * registered interceptor would attach the internal secret to every outbound Feign call in the
 * service, including ones that have no business seeing it.
 * <p>
 * The interceptor itself now comes from the shared {@code tenant-context} library
 * ({@link InternalTokenRequestInterceptor}), the same place order-service's inbound filter takes its
 * logic from, so both ends of the contract move together instead of drifting as two hand-written
 * copies. It sends:
 * <ul>
 *   <li>{@code X-Internal-Token} — the shared secret, so order-service accepts the call;</li>
 *   <li>{@code X-Internal-Caller} — this service's identity, so the callee can attribute, log and if
 *       needed revoke <em>this</em> caller rather than "whoever holds the secret".</li>
 * </ul>
 * <p>
 * Fail-closed: a blank token means the header is simply not sent, so order-service answers 401 and
 * {@code ReviewService} maps that to 503 — a review is never accepted without a verified purchase.
 * Neither value is ever logged. Full design: {@code docs/engineering/service-to-service-auth.md}.
 *
 * @author vamuhong
 * @version 2.0
 */
public class OrderClientConfig {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /**
     * @param internalToken the shared secret; falls back to the legacy single-secret property so an
     *                      existing deployment keeps working without touching its configuration
     * @param caller        this service's identity; blank simply omits the header, which a callee
     *                      still accepts while {@code require-caller-header} is off
     */
    @Bean
    public RequestInterceptor internalTokenInterceptor(
            @Value("${application.security.internal.token:${application.security.internal-token:}}")
            String internalToken,
            @Value("${application.security.internal.caller:product-service}") String caller) {
        return new InternalTokenRequestInterceptor(internalToken, caller);
    }
}
