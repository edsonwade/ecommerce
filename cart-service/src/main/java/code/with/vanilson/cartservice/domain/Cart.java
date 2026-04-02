package code.with.vanilson.cartservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cart — Domain Entity (Redis Hash)
 * <p>
 * Represents a customer's shopping cart. Stored entirely in Redis — no SQL DB.
 * <p>
 * Architecture decisions:
 * - @RedisHash maps this to a Redis Hash structure with key: "cart:{id}"
 * - @TimeToLive: cart expires after 24h of inactivity (TTL in seconds)
 * - @Indexed on customerId: allows lookup by customerId (secondary index in Redis)
 * - Serializable: required for Redis serialisation
 * <p>
 * CAP theorem trade-off: Redis = AP (Available + Partition tolerant).
 * If Redis is unavailable, cart operations fail gracefully — the customer's
 * browsing session is interrupted but no financial data is lost.
 * Orders (CP) are separate from carts (AP) — correct boundary.
 * <p>
 * Cart lifecycle:
 * 1. Created on first addItem call
 * 2. TTL reset on every mutation (24h sliding window)
 * 3. Cleared after checkout (cart is serialised into an Order)
 * 4. Expired automatically by Redis after TTL
 * </p>
 * <p>
 * Phase 4 — Multi-tenancy:
 * Tenant isolation is achieved via {@code tenantId} field with {@code @Indexed}.
 * The service layer uses {@code TenantContext.getCurrentTenantId()} to scope
 * all cart operations. Redis key format: {@code cart:{tenantId}:{customerId}}.
 * No Hibernate filter is involved — this is a Redis-only entity.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "cart")
public class Cart implements Serializable {

    @Id
    private String cartId;          // format: "{tenantId}:{customerId}" for deterministic tenant-scoped lookup

    /**
     * Phase 4: Tenant isolation — every cart belongs to exactly one tenant.
     * Indexed in Redis for efficient lookup: findByTenantIdAndCustomerId().
     * Set from TenantContext on cart creation; immutable after that.
     */
    @Indexed
    private String tenantId;

    @Indexed
    private String customerId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt  = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt  = LocalDateTime.now();

    /**
     * TTL: 86400 seconds = 24 hours.
     * Every write resets this — sliding window TTL.
     * Redis removes the key automatically when TTL expires.
     */
    @TimeToLive(unit = TimeUnit.SECONDS)
    @Builder.Default
    private Long ttl = 86400L;

    // -------------------------------------------------------
    // Domain behaviour — no business logic leaks to the service
    // -------------------------------------------------------

    /** Calculates the total cart value (sum of all line totals). */
    public BigDecimal getTotal() {
        return items.stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Returns the number of distinct product lines in the cart. */
    public int getItemCount() {
        return items.size();
    }

    /** Returns total quantity of all items in the cart. */
    public double getTotalQuantity() {
        return items.stream().mapToDouble(CartItem::getQuantity).sum();
    }

    /** Finds an existing item by productId. */
    public java.util.Optional<CartItem> findItem(Integer productId) {
        return items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();
    }

    /** Touch updatedAt and reset TTL on every write. */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
        this.ttl       = 86400L;
    }
}
