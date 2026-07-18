package code.with.vanilson.orderservice.internal;

import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.orderLine.OrderLineRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * InternalPurchaseController — service-to-service purchase verification (F7, Task 7.1).
 * <p>
 * Behind the {@code /internal} trust boundary (see {@link InternalTokenFilter}). product-service
 * calls this to answer "did this customer actually buy this product?" before accepting a review —
 * the source of truth for a verified purchase lives here, in order-service, and is never trusted
 * from the client. Only orders in a fulfilled state ({@link OrderStatus#CONFIRMED},
 * {@link OrderStatus#SHIPPED}, {@link OrderStatus#DELIVERED}) count as a purchase; REQUESTED /
 * PENDING_PAYMENT / CANCELLED / REFUNDED do not.
 * <p>
 * Tenant scoping (T2): the caller's {@code X-Tenant-ID} is auto-propagated by the Feign
 * {@code TenantFeignInterceptor} and repopulated into the {@code TenantContext} by the
 * {@code TenantInterceptor} on entry — no tenant parameter crosses the wire. The query is keyed on
 * {@code customerId + productId + status}; since {@code productId} belongs to exactly one tenant's
 * product, the pair already fixes the tenant, so the answer is correct independent of the read
 * {@code @Filter}.
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders/internal")
@RequiredArgsConstructor
@Tag(name = "Order Internal API", description = "Service-to-service only — guarded by X-Internal-Token")
public class InternalPurchaseController {

    /** Order states that count as a completed purchase for review eligibility. */
    private static final List<OrderStatus> FULFILLED_STATUSES =
            List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderLineRepository orderLineRepository;

    /**
     * @param customerId the buyer's userId (as stored on {@code customer_order.customer_id})
     * @param productId  the product to check
     * @return {@code {"purchased": true}} iff the customer has ≥1 fulfilled order line for the product
     */
    @Operation(summary = "Check whether a customer has a fulfilled purchase of a product (S2S only)")
    @GetMapping("/purchases/exists")
    public ResponseEntity<PurchaseExistsResponse> hasPurchased(
            @RequestParam("customerId") String customerId,
            @RequestParam("productId") Integer productId) {
        boolean purchased = orderLineRepository
                .existsPurchasedProduct(customerId, productId, FULFILLED_STATUSES);
        log.debug("[InternalPurchase] purchase check customerId=[{}] productId=[{}] purchased=[{}]",
                customerId, productId, purchased);
        return ResponseEntity.ok(new PurchaseExistsResponse(purchased));
    }
}
