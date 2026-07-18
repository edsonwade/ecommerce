package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductStatus;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.productservice.exception.ReviewVerificationException;
import code.with.vanilson.productservice.review.OrderClient;
import code.with.vanilson.productservice.review.PurchaseVerificationResponse;
import code.with.vanilson.productservice.review.Review;
import code.with.vanilson.productservice.review.ReviewRepository;
import code.with.vanilson.productservice.review.ReviewRequest;
import code.with.vanilson.productservice.review.ReviewResponse;
import code.with.vanilson.productservice.review.ReviewService;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ReviewSteps — BDD step definitions for the F7 review lifecycle (product_reviews.feature).
 * <p>
 * POJO + Mockito, matching {@link CategorySteps}: a real {@link ReviewService} runs over mocked
 * repositories + a mocked {@link OrderClient}, so scenarios exercise the real guards without a DB or
 * a live order-service. Step phrasing is prefixed "review feature:" — the Cucumber glue package is
 * shared, so patterns must not collide with the other suites.
 *
 * @author vamuhong
 */
public class ReviewSteps {

    private static final String TENANT = "test-tenant";

    private ReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private OrderClient orderClient;
    private ReviewService reviewService;

    private ReviewResponse created;
    private boolean deleted;
    private Exception caught;

    @Before
    public void init() {
        reviewRepository = Mockito.mock(ReviewRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        orderClient = Mockito.mock(OrderClient.class);
        MessageSource messageSource = Mockito.mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Product 1 exists and is ACTIVE.
        when(productRepository.findByIdAndTenantId(anyInt(), anyString()))
                .thenReturn(Optional.of(Product.builder().id(1).name("Widget")
                        .status(ProductStatus.ACTIVE).tenantId(TENANT).createdBy("9001").build()));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        reviewService = new ReviewService(reviewRepository, productRepository, orderClient, messageSource);

        // NOTE: do NOT set TenantContext here. This @Before runs before EVERY scenario in the shared
        // Cucumber glue package, and setting the tenant ThreadLocal leaked into other suites
        // (e.g. ProductStatusSteps), flipping getProductById onto the tenant-scoped query and 404-ing.
        // The tenant is set only inside this suite's own steps, via authenticateAs().
        created = null;
        deleted = false;
        caught = null;
    }

    @After
    public void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---- Given ----

    @Given("review feature: customer {long} has a verified purchase of product {int}")
    public void customer_has_verified_purchase(Long customerId, Integer productId) {
        when(orderClient.hasPurchased(String.valueOf(customerId), productId))
                .thenReturn(new PurchaseVerificationResponse(true));
    }

    @Given("review feature: customer {long} has NOT purchased product {int}")
    public void customer_has_not_purchased(Long customerId, Integer productId) {
        when(orderClient.hasPurchased(String.valueOf(customerId), productId))
                .thenReturn(new PurchaseVerificationResponse(false));
    }

    @Given("review feature: the purchase-verification service is unavailable")
    public void verification_unavailable() {
        when(orderClient.hasPurchased(anyString(), anyInt())).thenThrow(CallNotPermittedException.class);
    }

    @Given("review feature: customer {long} has already reviewed product {int}")
    public void customer_already_reviewed(Long customerId, Integer productId) {
        when(reviewRepository.existsByProductIdAndCustomerId(productId, customerId)).thenReturn(true);
    }

    @Given("review feature: a review {long} by customer {long} on product {int} exists")
    public void a_review_exists(Long reviewId, Long customerId, Integer productId) {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(
                Review.builder().id(reviewId).productId(productId).customerId(customerId)
                        .rating(4).tenantId(TENANT).createdAt(LocalDateTime.now()).build()));
    }

    // ---- When ----

    @When("review feature: customer {long} posts a {int} star review for product {int}")
    public void customer_posts_review(Long customerId, Integer rating, Integer productId) {
        authenticateAs(customerId, "USER");
        try {
            created = reviewService.createReview(productId, new ReviewRequest(rating, "from BDD"));
        } catch (Exception e) {
            caught = e;
        }
    }

    @When("review feature: admin {long} deletes review {long}")
    public void admin_deletes_review(Long adminId, Long reviewId) {
        authenticateAs(adminId, "ADMIN");
        invokeDelete(reviewId);
    }

    @When("review feature: customer {long} deletes review {long}")
    public void customer_deletes_review(Long customerId, Long reviewId) {
        authenticateAs(customerId, "USER");
        invokeDelete(reviewId);
    }

    // ---- Then ----

    @Then("review feature: the review is created")
    public void the_review_is_created() {
        assertThat(caught).isNull();
        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(100L);
    }

    @Then("review feature: the review is rejected as not-purchased")
    public void rejected_as_not_purchased() {
        assertThat(caught).isInstanceOf(ProductForbiddenException.class);
        assertThat(((ProductForbiddenException) caught).getMessageKey()).isEqualTo("review.not.purchased");
    }

    @Then("review feature: the review is rejected as duplicate")
    public void rejected_as_duplicate() {
        assertThat(caught).isInstanceOf(ProductConflictException.class);
        assertThat(((ProductConflictException) caught).getMessageKey()).isEqualTo("review.already.exists");
    }

    @Then("review feature: the review is rejected as verification-unavailable")
    public void rejected_as_verification_unavailable() {
        assertThat(caught).isInstanceOf(ReviewVerificationException.class);
        assertThat(((ReviewVerificationException) caught).getMessageKey())
                .isEqualTo("review.verification.unavailable");
    }

    @Then("review feature: the review is deleted")
    public void the_review_is_deleted() {
        assertThat(caught).isNull();
        assertThat(deleted).isTrue();
    }

    @Then("review feature: the delete is rejected as forbidden")
    public void delete_rejected_forbidden() {
        assertThat(caught).isInstanceOf(ProductForbiddenException.class);
        assertThat(((ProductForbiddenException) caught).getMessageKey()).isEqualTo("review.delete.forbidden");
    }

    // ---- Helpers ----

    private void invokeDelete(Long reviewId) {
        try {
            reviewService.deleteReview(reviewId);
            deleted = true;
        } catch (Exception e) {
            caught = e;
        }
    }

    private void authenticateAs(long userId, String role) {
        // Set the tenant only for this suite's own actions (never in @Before) — see init() note.
        TenantContext.setCurrentTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user@test.com", userId, TENANT, role), null, List.of()));
    }
}
