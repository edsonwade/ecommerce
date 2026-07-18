package code.with.vanilson.productservice.review;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * OrderClient — Feign client for the F7 purchase-verification gate.
 * <p>
 * Calls order-service's {@code /internal} endpoint directly on {@code services-net} (bypassing the
 * gateway), attaching the {@code X-Internal-Token} shared secret via {@link OrderClientConfig}; the
 * {@code X-Tenant-ID} header is auto-propagated by the tenant-context Feign interceptor (T2). Wrapped
 * in a circuit breaker ({@code order-purchase-verification}) with short timeouts (product-service.yml)
 * so a slow/absent order-service surfaces quickly. There is intentionally <b>no fallback</b>: on
 * timeout/error/open-circuit the exception propagates and {@link ReviewService} fails the review write
 * closed (503) — a review is never accepted without a verified purchase (B1).
 *
 * @author vamuhong
 * @version 1.0
 */
@FeignClient(
        name = "order-purchase-verification",
        url = "${application.config.order-url:http://order-service:8083/api/v1/orders}",
        configuration = OrderClientConfig.class)
public interface OrderClient {

    @CircuitBreaker(name = "order-purchase-verification")
    @GetMapping("/internal/purchases/exists")
    PurchaseVerificationResponse hasPurchased(@RequestParam("customerId") String customerId,
                                              @RequestParam("productId") Integer productId);
}
