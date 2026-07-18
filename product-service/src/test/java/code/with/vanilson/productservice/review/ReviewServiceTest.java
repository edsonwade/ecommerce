package code.with.vanilson.productservice.review;

import code.with.vanilson.productservice.Product;
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
import org.mockito.Mockito;
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
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = Mockito.mock(ReviewRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        orderClient = Mockito.mock(OrderClient.class);
        messageSource = Mockito.mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service = new ReviewService(reviewRepository, productRepository, orderClient, messageSource);

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
}
