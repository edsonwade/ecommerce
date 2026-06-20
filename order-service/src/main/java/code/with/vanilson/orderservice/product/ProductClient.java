package code.with.vanilson.orderservice.product;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

/**
 * ProductClient
 * <p>
 * Feign client owned by order-service. Resolves a product's owning seller
 * ({@code createdBy}) so each order line can be stamped with its seller_id at
 * creation — this is what lets a seller see exactly the orders placed for their
 * own products.
 * <p>
 * Targets product-service's public {@code GET /api/v1/products/{id}} via
 * {@code application.config.product-url}. The {@link code.with.vanilson.tenantcontext.TenantFeignInterceptor}
 * forwards the caller's Authorization + X-Tenant-Id automatically. Failures are
 * handled defensively at the call site (see OrderLineService) so a transient
 * product-service hiccup never blocks checkout.
 *
 * @author vamuhong
 * @version 1.0
 */
@FeignClient(
        name = "product-service",
        url = "${application.config.product-url}"
)
public interface ProductClient {

    @GetMapping("/{product-id}")
    Optional<ProductOwnerResponse> getProductById(@PathVariable("product-id") Integer productId);
}
