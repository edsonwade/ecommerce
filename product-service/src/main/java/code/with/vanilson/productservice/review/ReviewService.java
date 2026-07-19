package code.with.vanilson.productservice.review;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductCacheKeys;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductStatus;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ReviewVerificationException;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ReviewService — Application Layer (F7).
 * <p>
 * Owns the review lifecycle and its guards: a review may only be written by a customer with a
 * verified fulfilled purchase (checked out-of-band against order-service, fail-closed), at most one
 * per customer/product, and only on a live (non-suspended) product. Deletes are restricted to the
 * review's owner or an ADMIN (sellers do NOT moderate) and are recorded in a structured audit log.
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderClient orderClient;
    private final MessageSource messageSource;
    private final CacheManager cacheManager;

    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         OrderClient orderClient,
                         MessageSource messageSource,
                         CacheManager cacheManager) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderClient = orderClient;
        this.messageSource = messageSource;
        this.cacheManager = cacheManager;
    }

    // -------------------------------------------------------
    // WRITE
    // -------------------------------------------------------

    /**
     * Creates a review after verifying the caller bought the product. Order of guards:
     * product must exist and be active (404) → purchase must be verifiable and true
     * (503 if verification is unavailable, 403 if the caller never bought it) → no existing
     * review for this customer/product (409) → persist.
     */
    @Transactional
    public ReviewResponse createReview(int productId, ReviewRequest request) {
        requireActiveProduct(productId);
        SecurityPrincipal principal = requirePrincipal();
        long customerId = principal.userId();

        verifyPurchase(customerId, productId);

        if (reviewRepository.existsByProductIdAndCustomerId(productId, customerId)) {
            throw new ProductConflictException(
                    resolve("review.already.exists"), "review.already.exists");
        }

        Review saved = reviewRepository.save(Review.builder()
                .productId(productId)
                .customerId(customerId)
                .rating(request.rating())
                .comment(request.comment())
                .tenantId(currentTenant())
                .createdAt(LocalDateTime.now())
                .build());

        log.info(resolve("review.log.created", saved.getId(), productId, customerId));
        refreshProductRating(productId);
        return toResponse(saved);
    }

    /**
     * Fail-closed purchase check: any failure calling order-service (timeout, error, open circuit)
     * is turned into a 503 — we never accept a review we could not verify (B1). A verified
     * "not purchased" is a 403.
     */
    private void verifyPurchase(long customerId, int productId) {
        PurchaseVerificationResponse verification;
        try {
            verification = orderClient.hasPurchased(String.valueOf(customerId), productId);
        } catch (Exception ex) {
            // Deliberately broad: ANY problem verifying the purchase must fail closed (503),
            // never fall through to accepting an unverified review. Token/CB/timeout/error all land here.
            log.warn("[ReviewService] purchase verification unavailable productId=[{}] customerId=[{}] cause=[{}]",
                    productId, customerId, ex.getClass().getSimpleName());
            throw new ReviewVerificationException(
                    resolve("review.verification.unavailable"), "review.verification.unavailable", ex);
        }
        if (verification == null || !verification.purchased()) {
            throw new ProductForbiddenException(
                    resolve("review.not.purchased"), "review.not.purchased");
        }
    }

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    /** Paginated reviews for a live product (suspended/absent → 404, D4). */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviews(int productId, Pageable pageable) {
        requireActiveProduct(productId);
        return reviewRepository.findByProductId(productId, pageable).map(this::toResponse);
    }

    // -------------------------------------------------------
    // DELETE (moderation / owner)
    // -------------------------------------------------------

    /**
     * Deletes a review. Allowed only for an ADMIN (moderation) or the review's own author.
     * Sellers cannot moderate. Emits a structured audit line (who / reviewId / productId / reason).
     */
    @Transactional
    public void deleteReview(long reviewId) {
        SecurityPrincipal principal = requirePrincipal();
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ProductNotFoundException(
                        resolve("review.not.found", reviewId), "review.not.found"));

        boolean isOwner = review.getCustomerId() != null
                && review.getCustomerId().equals(principal.userId());
        if (!principal.isAdmin() && !isOwner) {
            throw new ProductForbiddenException(
                    resolve("review.delete.forbidden"), "review.delete.forbidden");
        }

        // Captured BEFORE the delete: after it, the entity is detached and we still need the
        // product whose counters must be recomputed.
        int productId = review.getProductId();

        reviewRepository.delete(review);
        String reason = principal.isAdmin() && !isOwner ? "ADMIN_MODERATION" : "OWNER";
        log.info("[ReviewModeration] review deleted reviewId=[{}] productId=[{}] by userId=[{}] reason=[{}]",
                reviewId, productId, principal.userId(), reason);
        refreshProductRating(productId);
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    /**
     * Fase 7 Task 7.3 (Decision A1): brings the product's denormalised rating counters back in sync
     * after a review was written or removed, then refreshes the cached detail.
     * <p>
     * Runs inside the caller's transaction, so the recompute commits atomically with the review
     * change — the counters can never be left describing a review set that was rolled back. The
     * recompute itself is a single row-locking UPDATE that reads COUNT/AVG from source; see
     * {@link ProductRepository#recomputeRatingCounters(int)} for the concurrency argument.
     *
     * @param productId the product whose counters changed
     */
    private void refreshProductRating(int productId) {
        productRepository.recomputeRatingCounters(productId);
        evictProductDetail(productId);
        log.info(resolve("review.log.rating.refreshed", productId));
    }

    /**
     * Evicts ONLY this product's cached detail entry.
     * <p>
     * <strong>Decision A1 — "no-evict" on the catalogue list, and it is deliberate:</strong> the
     * paginated {@code product-list} cache is intentionally left alone so a review write never
     * invalidates whole catalogue pages. Product cards therefore show the previous average until the
     * list TTL expires, while the product detail page is always fresh. That asymmetry is the entire
     * point: it keeps {@code GET /products} latency flat (the stated goal of the 7b verification)
     * for a number whose staleness is cosmetic. Do NOT "fix" this by adding an
     * {@code allEntries = true} evict on {@code product-list}.
     * <p>
     * The key is built through {@link ProductCacheKeys} so it matches
     * {@code ProductService.getProductById}'s {@code @Cacheable} key exactly — a hand-rolled key
     * (notably one using this class's {@code "default"} tenant fallback instead of {@code "none"})
     * would silently evict nothing.
     *
     * @param productId the product whose cached detail is now stale
     */
    private void evictProductDetail(int productId) {
        Cache cache = cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCTS);
        if (cache != null) {
            cache.evict(ProductCacheKeys.detailKey(
                    TenantContext.isPresent() ? TenantContext.getCurrentTenantId() : null, productId));
        }
    }

    private void requireActiveProduct(int productId) {
        Optional<Product> found = TenantContext.isPresent()
                ? productRepository.findByIdAndTenantId(productId, TenantContext.getCurrentTenantId())
                : productRepository.findById(productId);
        Product product = found.orElseThrow(() -> new ProductNotFoundException(
                resolve("product.not.found", productId), "product.not.found"));
        // Reviews on a suspended product are hidden from everyone (D4) — indistinguishable from 404.
        if (product.getStatus() == ProductStatus.SUSPENDED) {
            throw new ProductNotFoundException(
                    resolve("product.not.found", productId), "product.not.found");
        }
    }

    private SecurityPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal;
        }
        // Endpoints are authenticated by the security chain; a missing principal is treated as forbidden.
        throw new ProductForbiddenException(resolve("review.delete.forbidden"), "review.delete.forbidden");
    }

    private String currentTenant() {
        return TenantContext.isPresent() ? TenantContext.getCurrentTenantId() : "default";
    }

    private ReviewResponse toResponse(Review r) {
        return new ReviewResponse(
                r.getId(), r.getProductId(), r.getCustomerId(), r.getRating(), r.getComment(), r.getCreatedAt());
    }

    private String resolve(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
