package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductCacheKeys;
import code.with.vanilson.productservice.ProductMapper;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductResponse;
import code.with.vanilson.productservice.ProductStatus;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.review.OrderClient;
import code.with.vanilson.productservice.review.PurchaseVerificationResponse;
import code.with.vanilson.productservice.review.Review;
import code.with.vanilson.productservice.review.ReviewRepository;
import code.with.vanilson.productservice.review.ReviewRequest;
import code.with.vanilson.productservice.review.ReviewService;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * CatalogRatingSteps — BDD glue for {@code catalog_rating.feature} (Fase 7 Task 7.3).
 * <p>
 * POJO + Mockito, like the rest of this suite. The mocked {@link ProductRepository} stands in for the
 * real recompute UPDATE: {@code recomputeRatingCounters} recalculates the average and count from the
 * in-memory review list and writes them onto the product, exactly as the SQL does (including the
 * one-decimal rounding). Real DB behaviour — the row-lock, and the concurrency guarantee — is proven
 * separately by {@code ReviewConcurrencyIntegrationTest}, which a mock could never demonstrate.
 * <p>
 * Step text carries a {@code "catalog rating: "} prefix because Cucumber requires globally unique
 * step text across the whole glue package, which is shared with the other product-service suites.
 */
public class CatalogRatingSteps {

    private static final String TENANT = "test-tenant";
    private static final int PRODUCT_ID = 1;

    private ReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private OrderClient orderClient;
    private CacheManager cacheManager;
    private ProductMapper productMapper;
    private ReviewService reviewService;

    /** Stands in for the product_review rows of product 1. */
    private final List<Review> reviews = new ArrayList<>();
    private final AtomicLong reviewIds = new AtomicLong(1);
    private Product product;
    private String cachedPageKey;

    @Before
    public void init() {
        reviewRepository = Mockito.mock(ReviewRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        orderClient = Mockito.mock(OrderClient.class);
        MessageSource messageSource = Mockito.mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        cacheManager = new ConcurrentMapCacheManager(
                ProductCacheKeys.CACHE_PRODUCTS, ProductCacheKeys.CACHE_PRODUCT_LIST);
        productMapper = new ProductMapper(messageSource);

        reviews.clear();
        reviewIds.set(1);
        cachedPageKey = null;

        reviewService = new ReviewService(
                reviewRepository, productRepository, orderClient, messageSource, cacheManager);

        // NOTE: like ReviewSteps, the tenant ThreadLocal is NOT bound here — this @Before runs for
        // EVERY scenario in the shared glue package and would leak into the other suites. It is
        // bound inside this suite's own Given step instead.
    }

    @After
    public void cleanUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------
    // Given
    // -------------------------------------------------------

    @Given("catalog rating: product {int} exists and is active")
    public void productExistsAndIsActive(int productId) {
        TenantContext.setCurrentTenantId(TENANT);
        product = Product.builder()
                .id(productId).name("Rated-Widget").description("collects stars")
                .availableQuantity(10.0).price(BigDecimal.valueOf(25))
                .category(Category.builder().id(1).name("Electronics").description("Devices").build())
                .tenantId(TENANT).createdBy("9001").status(ProductStatus.ACTIVE)
                .build();

        // Both lookup branches are stubbed on purpose: requireActiveProduct picks its query from the
        // ambient TenantContext, and stubbing only one branch is exactly what broke the F5 suite.
        when(productRepository.findByIdAndTenantId(anyInt(), anyString())).thenReturn(Optional.of(product));
        when(productRepository.findById(anyInt())).thenReturn(Optional.of(product));

        when(orderClient.hasPurchased(anyString(), anyInt())).thenReturn(new PurchaseVerificationResponse(true));
        when(reviewRepository.existsByProductIdAndCustomerId(anyInt(), anyLong())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(reviewIds.getAndIncrement());
            reviews.add(r);
            return r;
        });
        when(reviewRepository.findById(anyLong())).thenAnswer(inv -> reviews.stream()
                .filter(r -> r.getId().equals(inv.getArgument(0)))
                .findFirst());
        Mockito.doAnswer(inv -> {
            reviews.removeIf(r -> r.getId().equals(((Review) inv.getArgument(0)).getId()));
            return null;
        }).when(reviewRepository).delete(any(Review.class));

        // The mocked stand-in for the recompute-from-source UPDATE.
        when(productRepository.recomputeRatingCounters(anyInt())).thenAnswer(inv -> {
            recomputeOntoProduct();
            return 1;
        });
    }

    @Given("catalog rating: product {int} already has ratings {int} and {int}")
    public void productAlreadyHasTwoRatings(int productId, int first, int second) {
        seedReview(101L, first);
        seedReview(102L, second);
        recomputeOntoProduct();
    }

    @Given("catalog rating: product {int} already has ratings {int}")
    public void productAlreadyHasOneRating(int productId, int only) {
        seedReview(101L, only);
        recomputeOntoProduct();
    }

    @Given("catalog rating: the product detail and a catalogue page are cached")
    public void detailAndPageAreCached() {
        cachedPageKey = "all-0-20";
        cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCTS)
                .put(ProductCacheKeys.detailKey(TENANT, PRODUCT_ID), "stale-detail");
        cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCT_LIST).put(cachedPageKey, "stale-page");
    }

    // -------------------------------------------------------
    // When
    // -------------------------------------------------------

    @When("catalog rating: customer {long} posts a {int} star review for product {int}")
    public void customerPostsReview(long customerId, int rating, int productId) {
        authenticateAs(customerId);
        reviewService.createReview(productId, new ReviewRequest(rating, "comment-" + rating));
    }

    @When("catalog rating: the review rated {int} is removed")
    public void reviewRatedIsRemoved(int rating) {
        Review target = reviews.stream()
                .filter(r -> r.getRating() == rating)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seeded review rated " + rating));
        authenticateAs(target.getCustomerId());
        reviewService.deleteReview(target.getId());
    }

    // -------------------------------------------------------
    // Then
    // -------------------------------------------------------

    @Then("catalog rating: the catalog shows an average of {string} from {int} reviews")
    public void catalogShowsAverageAndCount(String expectedAverage, int expectedCount) {
        // Asserted through the mapper, i.e. the actual catalogue surface, not the entity.
        ProductResponse response = productMapper.toProductResp(product);
        assertThat(response.averageRating()).isEqualByComparingTo(expectedAverage);
        assertThat(response.reviewCount()).isEqualTo(expectedCount);
    }

    @Then("catalog rating: the cached product detail was discarded")
    public void cachedDetailDiscarded() {
        assertThat(cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCTS)
                .get(ProductCacheKeys.detailKey(TENANT, PRODUCT_ID))).isNull();
    }

    @Then("catalog rating: the cached catalogue page survived")
    public void cachedPageSurvived() {
        assertThat(cacheManager.getCache(ProductCacheKeys.CACHE_PRODUCT_LIST).get(cachedPageKey))
                .as("Decision A1 no-evict: catalogue pages must outlive a review write")
                .isNotNull();
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private void seedReview(long customerId, int rating) {
        reviews.add(Review.builder().id(reviewIds.getAndIncrement()).productId(PRODUCT_ID)
                .customerId(customerId).rating(rating).tenantId(TENANT)
                .createdAt(LocalDateTime.now()).build());
    }

    /** Mirrors {@code SET review_count = COUNT(*), average_rating = COALESCE(ROUND(AVG(rating),1), 0)}. */
    private void recomputeOntoProduct() {
        int count = reviews.size();
        product.setReviewCount(count);
        product.setAverageRating(count == 0
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(reviews.stream().mapToInt(Review::getRating).sum())
                        .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));
    }

    private void authenticateAs(long userId) {
        TenantContext.setCurrentTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user" + userId + "@test.com", userId, TENANT, "USER"),
                        null, List.of()));
    }
}
