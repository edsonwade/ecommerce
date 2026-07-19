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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReviewServiceTest — unit tests (JUnit 5 + Mockito) for the F7 review lifecycle and its guards.
 * A real {@link ReviewService} runs over mocked collaborators; the purchase-verification Feign call
 * and the DB are mocked, so each guard (404 / 403 / 409 / 503 / delete-authz) is exercised in isolation.
 *
 * @author vamuhong
 * @version 1.0
 */
@DisplayName("ReviewService — unit")
class ReviewServiceTest {

    private static final String TENANT = "test-tenant";
    private static final int PRODUCT_ID = 1;
    private static final long CUSTOMER_ID = 42L;

    private ReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private OrderClient orderClient;
    private MessageSource messageSource;
    // A REAL cache manager (not a mock): Task 7.3's cache policy is about which entries actually
    // survive a review write, and only a real cache can prove that.
    private CacheManager cacheManager;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = Mockito.mock(ReviewRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        orderClient = Mockito.mock(OrderClient.class);
        messageSource = Mockito.mock(MessageSource.class);
        cacheManager = new ConcurrentMapCacheManager(
                ProductCacheKeys.CACHE_PRODUCTS, ProductCacheKeys.CACHE_PRODUCT_LIST);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service = new ReviewService(
                reviewRepository, productRepository, orderClient, messageSource, cacheManager);

        TenantContext.setCurrentTenantId(TENANT);
        // Product 1 exists and is ACTIVE by default.
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT))
                .thenReturn(Optional.of(activeProduct()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static Product activeProduct() {
        return Product.builder().id(PRODUCT_ID).name("Widget")
                .status(ProductStatus.ACTIVE).tenantId(TENANT).createdBy("9001").build();
    }

    private void authenticateAs(long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user@test.com", userId, TENANT, role), null, List.of()));
    }

    @Nested
    @DisplayName("createReview")
    class CreateReview {

        @Test
        @DisplayName("verified buyer → persists and returns the review")
        void verifiedBuyerCreates() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(orderClient.hasPurchased("42", PRODUCT_ID)).thenReturn(new PurchaseVerificationResponse(true));
            when(reviewRepository.existsByProductIdAndCustomerId(PRODUCT_ID, CUSTOMER_ID)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(100L);
                return r;
            });

            ReviewResponse resp = service.createReview(PRODUCT_ID, new ReviewRequest(5, "Great"));

            assertThat(resp.id()).isEqualTo(100L);
            assertThat(resp.rating()).isEqualTo(5);
            assertThat(resp.customerId()).isEqualTo(CUSTOMER_ID);
            verify(reviewRepository).save(any(Review.class));
        }

        @Test
        @DisplayName("caller never purchased → 403 review.not.purchased, nothing saved")
        void notPurchasedForbidden() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(orderClient.hasPurchased("42", PRODUCT_ID)).thenReturn(new PurchaseVerificationResponse(false));

            assertThatThrownBy(() -> service.createReview(PRODUCT_ID, new ReviewRequest(4, null)))
                    .isInstanceOf(ProductForbiddenException.class)
                    .satisfies(ex -> assertThat(((ProductForbiddenException) ex).getMessageKey())
                            .isEqualTo("review.not.purchased"));
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("verification unavailable (circuit open) → 503 fail-closed, nothing saved")
        void verificationUnavailableFailsClosed() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(orderClient.hasPurchased("42", PRODUCT_ID))
                    .thenThrow(CallNotPermittedException.class);

            assertThatThrownBy(() -> service.createReview(PRODUCT_ID, new ReviewRequest(4, null)))
                    .isInstanceOf(ReviewVerificationException.class)
                    .satisfies(ex -> assertThat(((ReviewVerificationException) ex).getMessageKey())
                            .isEqualTo("review.verification.unavailable"));
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("already reviewed → 409 review.already.exists")
        void duplicateConflict() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(orderClient.hasPurchased("42", PRODUCT_ID)).thenReturn(new PurchaseVerificationResponse(true));
            when(reviewRepository.existsByProductIdAndCustomerId(PRODUCT_ID, CUSTOMER_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.createReview(PRODUCT_ID, new ReviewRequest(3, "again")))
                    .isInstanceOf(ProductConflictException.class)
                    .satisfies(ex -> assertThat(((ProductConflictException) ex).getMessageKey())
                            .isEqualTo("review.already.exists"));
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("suspended product → 404, verification never called")
        void suspendedProductNotFound() {
            authenticateAs(CUSTOMER_ID, "USER");
            Product suspended = activeProduct();
            suspended.setStatus(ProductStatus.SUSPENDED);
            when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT)).thenReturn(Optional.of(suspended));

            assertThatThrownBy(() -> service.createReview(PRODUCT_ID, new ReviewRequest(5, null)))
                    .isInstanceOf(ProductNotFoundException.class);
            verify(orderClient, never()).hasPurchased(anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("getReviews")
    class GetReviews {

        @Test
        @DisplayName("active product → returns a page of reviews")
        void returnsPage() {
            Review r = Review.builder().id(1L).productId(PRODUCT_ID).customerId(CUSTOMER_ID)
                    .rating(5).comment("nice").tenantId(TENANT).createdAt(LocalDateTime.now()).build();
            when(reviewRepository.findByProductId(eq(PRODUCT_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(r)));

            Page<ReviewResponse> page = service.getReviews(PRODUCT_ID, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).rating()).isEqualTo(5);
        }

        @Test
        @DisplayName("suspended product → 404")
        void suspendedProduct404() {
            Product suspended = activeProduct();
            suspended.setStatus(ProductStatus.SUSPENDED);
            when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT)).thenReturn(Optional.of(suspended));

            assertThatThrownBy(() -> service.getReviews(PRODUCT_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteReview")
    class DeleteReview {

        private Review existingReview() {
            return Review.builder().id(7L).productId(PRODUCT_ID).customerId(CUSTOMER_ID)
                    .rating(4).tenantId(TENANT).createdAt(LocalDateTime.now()).build();
        }

        @Test
        @DisplayName("owner deletes own review → removed")
        void ownerDeletes() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(reviewRepository.findById(7L)).thenReturn(Optional.of(existingReview()));

            service.deleteReview(7L);

            verify(reviewRepository).delete(any(Review.class));
        }

        @Test
        @DisplayName("ADMIN moderates another user's review → removed")
        void adminModerates() {
            authenticateAs(999L, "ADMIN");
            when(reviewRepository.findById(7L)).thenReturn(Optional.of(existingReview()));

            service.deleteReview(7L);

            verify(reviewRepository).delete(any(Review.class));
        }

        @Test
        @DisplayName("another non-admin user → 403 review.delete.forbidden, nothing deleted")
        void foreignUserForbidden() {
            authenticateAs(555L, "USER");
            when(reviewRepository.findById(7L)).thenReturn(Optional.of(existingReview()));

            assertThatThrownBy(() -> service.deleteReview(7L))
                    .isInstanceOf(ProductForbiddenException.class)
                    .satisfies(ex -> assertThat(((ProductForbiddenException) ex).getMessageKey())
                            .isEqualTo("review.delete.forbidden"));
            verify(reviewRepository, never()).delete(any());
        }

        @Test
        @DisplayName("unknown review id → 404")
        void unknownReview404() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(reviewRepository.findById(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteReview(7L))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    /**
     * Fase 7 Task 7.3 (Decision A1): the denormalised rating counters, and the deliberately
     * asymmetric cache policy that keeps the catalogue fast.
     */
    @Nested
    @DisplayName("rating counters (Task 7.3)")
    class RatingCounters {

        /** The exact key ProductService.getProductById caches a detail under. */
        private final String detailKey = ProductCacheKeys.detailKey(TENANT, PRODUCT_ID);

        /** Seeds both caches so we can observe precisely which one a review write invalidates. */
        private void seedCaches() {
            cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCTS).put(detailKey, "stale-detail");
            cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCT_LIST).put("all-0-20", "stale-page");
        }

        private void stubSuccessfulCreate() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(orderClient.hasPurchased("42", PRODUCT_ID)).thenReturn(new PurchaseVerificationResponse(true));
            when(reviewRepository.existsByProductIdAndCustomerId(PRODUCT_ID, CUSTOMER_ID)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(100L);
                return r;
            });
        }

        @Test
        @DisplayName("create → recomputes the product's counters from source")
        void createRecomputes() {
            stubSuccessfulCreate();

            service.createReview(PRODUCT_ID, new ReviewRequest(5, "Great"));

            verify(productRepository).recomputeRatingCounters(PRODUCT_ID);
        }

        @Test
        @DisplayName("delete → recomputes the counters of the deleted review's product")
        void deleteRecomputes() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(reviewRepository.findById(7L)).thenReturn(Optional.of(
                    Review.builder().id(7L).productId(PRODUCT_ID).customerId(CUSTOMER_ID)
                            .rating(4).tenantId(TENANT).createdAt(LocalDateTime.now()).build()));

            service.deleteReview(7L);

            verify(productRepository).recomputeRatingCounters(PRODUCT_ID);
        }

        @Test
        @DisplayName("recompute is preceded by the row-lock, as its own statement (lost-update guard)")
        void recomputeTakesTheRowLockFirst() {
            stubSuccessfulCreate();

            service.createReview(PRODUCT_ID, new ReviewRequest(5, "Great"));

            // Order is the fix, not an implementation detail: locking inside the UPDATE would let
            // the sub-SELECTs keep the pre-lock snapshot and silently drop concurrent reviews.
            // ReviewConcurrencyIntegrationTest proves it against real PostgreSQL; this pins it cheaply.
            InOrder inOrder = inOrder(productRepository);
            inOrder.verify(productRepository).lockProductForRatingRecompute(PRODUCT_ID);
            inOrder.verify(productRepository).recomputeRatingCounters(PRODUCT_ID);
        }

        @Test
        @DisplayName("create → evicts THIS product's cached detail, leaves the catalogue list cached (no-evict)")
        void createEvictsDetailButNotList() {
            seedCaches();
            stubSuccessfulCreate();

            service.createReview(PRODUCT_ID, new ReviewRequest(5, "Great"));

            assertThat(cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCTS).get(detailKey))
                    .as("product detail must be evicted so the detail page shows the new average immediately")
                    .isNull();
            assertThat(cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCT_LIST).get("all-0-20"))
                    .as("Decision A1 no-evict: catalogue pages must SURVIVE a review write, "
                            + "so GET /products latency never regresses; cards refresh on TTL")
                    .isNotNull();
        }

        @Test
        @DisplayName("delete → same asymmetry: detail evicted, catalogue list untouched")
        void deleteEvictsDetailButNotList() {
            seedCaches();
            authenticateAs(CUSTOMER_ID, "USER");
            when(reviewRepository.findById(7L)).thenReturn(Optional.of(
                    Review.builder().id(7L).productId(PRODUCT_ID).customerId(CUSTOMER_ID)
                            .rating(4).tenantId(TENANT).createdAt(LocalDateTime.now()).build()));

            service.deleteReview(7L);

            assertThat(cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCTS).get(detailKey)).isNull();
            assertThat(cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCT_LIST).get("all-0-20")).isNotNull();
        }

        @Test
        @DisplayName("rejected write (never purchased) → no recompute, no eviction")
        void rejectedWriteLeavesEverythingAlone() {
            seedCaches();
            authenticateAs(CUSTOMER_ID, "USER");
            when(orderClient.hasPurchased("42", PRODUCT_ID)).thenReturn(new PurchaseVerificationResponse(false));

            assertThatThrownBy(() -> service.createReview(PRODUCT_ID, new ReviewRequest(4, null)))
                    .isInstanceOf(ProductForbiddenException.class);

            verify(productRepository, never()).recomputeRatingCounters(anyInt());
            assertThat(cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCTS).get(detailKey)).isNotNull();
        }
    }

    /**
     * Fase 7 Task 7.4a: the eligibility probe that decides whether the storefront renders the
     * "write a review" form. Its contract differs from createReview in one deliberate way — a
     * verification outage is reported, not thrown — so that difference is asserted explicitly.
     */
    @Nested
    @DisplayName("getEligibility (Task 7.4a)")
    class GetEligibility {

        @Test
        @DisplayName("verified purchase, no review yet → ELIGIBLE")
        void eligibleWhenPurchasedAndUnreviewed() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(reviewRepository.findByProductIdAndCustomerId(PRODUCT_ID, CUSTOMER_ID))
                    .thenReturn(Optional.empty());
            when(orderClient.hasPurchased("42", PRODUCT_ID)).thenReturn(new PurchaseVerificationResponse(true));

            ReviewEligibilityResponse result = service.getEligibility(PRODUCT_ID);

            assertThat(result.canReview()).isTrue();
            assertThat(result.reason()).isEqualTo(ReviewEligibilityResponse.Reason.ELIGIBLE);
            assertThat(result.existingReview()).isNull();
        }

        @Test
        @DisplayName("already reviewed → ALREADY_REVIEWED, carries the existing review, skips verification")
        void alreadyReviewedShortCircuits() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(reviewRepository.findByProductIdAndCustomerId(PRODUCT_ID, CUSTOMER_ID))
                    .thenReturn(Optional.of(Review.builder().id(11L).productId(PRODUCT_ID)
                            .customerId(CUSTOMER_ID).rating(4).comment("Solid")
                            .tenantId(TENANT).createdAt(LocalDateTime.now()).build()));

            ReviewEligibilityResponse result = service.getEligibility(PRODUCT_ID);

            assertThat(result.canReview()).isFalse();
            assertThat(result.reason()).isEqualTo(ReviewEligibilityResponse.Reason.ALREADY_REVIEWED);
            assertThat(result.existingReview().comment()).isEqualTo("Solid");
            // The existing review is decisive on its own — no reason to pay for a remote call.
            verify(orderClient, never()).hasPurchased(anyString(), anyInt());
        }

        @Test
        @DisplayName("never purchased → NOT_PURCHASED")
        void notPurchased() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(reviewRepository.findByProductIdAndCustomerId(PRODUCT_ID, CUSTOMER_ID))
                    .thenReturn(Optional.empty());
            when(orderClient.hasPurchased("42", PRODUCT_ID)).thenReturn(new PurchaseVerificationResponse(false));

            ReviewEligibilityResponse result = service.getEligibility(PRODUCT_ID);

            assertThat(result.canReview()).isFalse();
            assertThat(result.reason()).isEqualTo(ReviewEligibilityResponse.Reason.NOT_PURCHASED);
        }

        @Test
        @DisplayName("order-service down → VERIFICATION_UNAVAILABLE, NOT a 503 (the page must still render)")
        void verificationOutageIsReportedNotThrown() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(reviewRepository.findByProductIdAndCustomerId(PRODUCT_ID, CUSTOMER_ID))
                    .thenReturn(Optional.empty());
            when(orderClient.hasPurchased("42", PRODUCT_ID))
                    .thenThrow(CallNotPermittedException.class);

            ReviewEligibilityResponse result = service.getEligibility(PRODUCT_ID);

            assertThat(result.canReview())
                    .as("an outage may never grant the right to review")
                    .isFalse();
            assertThat(result.reason()).isEqualTo(ReviewEligibilityResponse.Reason.VERIFICATION_UNAVAILABLE);
        }

        @Test
        @DisplayName("suspended product → 404, same as every other review read")
        void suspendedProductIs404() {
            authenticateAs(CUSTOMER_ID, "USER");
            when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT)).thenReturn(Optional.of(
                    Product.builder().id(PRODUCT_ID).name("Widget")
                            .status(ProductStatus.SUSPENDED).tenantId(TENANT).createdBy("9001").build()));

            assertThatThrownBy(() -> service.getEligibility(PRODUCT_ID))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    /**
     * Fase 7 Task 7.4a: the ADMIN moderation feed. The service itself is thin — the guarantee worth
     * asserting here is that it scopes by tenant rather than returning every review in the table.
     */
    @Nested
    @DisplayName("getAllForModeration (Task 7.4a)")
    class GetAllForModeration {

        @Test
        @DisplayName("scopes the query to the caller's tenant")
        void scopesByTenant() {
            Page<AdminReviewResponse> page = new PageImpl<>(List.of(new AdminReviewResponse(
                    1L, PRODUCT_ID, "Widget", CUSTOMER_ID, 5, "Great", LocalDateTime.now())));
            when(reviewRepository.findAllForModeration(eq(TENANT), any())).thenReturn(page);

            Page<AdminReviewResponse> result = service.getAllForModeration(PageRequest.of(0, 20));

            assertThat(result.getContent()).singleElement()
                    .satisfies(r -> assertThat(r.productName()).isEqualTo("Widget"));
            verify(reviewRepository).findAllForModeration(eq(TENANT), any());
        }
    }
}
