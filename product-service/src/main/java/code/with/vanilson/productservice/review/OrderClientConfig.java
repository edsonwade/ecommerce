package code.with.vanilson.productservice.review;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * OrderClientConfig — per-client Feign configuration for {@link OrderClient} (F7).
 * <p>
 * Deliberately NOT annotated {@code @Configuration} / component-scanned: it is referenced only via
 * {@code @FeignClient(configuration = OrderClientConfig.class)}, so its interceptor applies to the
 * order client alone and never becomes a global default. Adds the {@code X-Internal-Token} shared
 * secret so order-service's {@code InternalTokenFilter} accepts the call. Fail-closed: a blank token
 * is simply not sent, so order-service rejects with 401 → ReviewService maps to 503. The secret is
 * read from config (Vault/env) and never logged.
 *
 * @author vamuhong
 * @version 1.0
 */
public class OrderClientConfig {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Bean
    public RequestInterceptor internalTokenInterceptor(
            @Value("${application.security.internal-token:}") String internalToken) {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header(INTERNAL_TOKEN_HEADER, internalToken);
            }
        };
    }
}
