package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductStatus;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ReviewVerificationException;
import code.with.vanilson.productservice.review.OrderClient;
import code.with.vanilson.productservice.review.PurchaseVerificationResponse;
import code.with.vanilson.productservice.review.ReviewRepository;
import code.with.vanilson.productservice.review.ReviewRequest;
import code.with.vanilson.productservice.review.ReviewResponse;
import code.with.vanilson.productservice.review.ReviewService;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ReviewIntegrationTest — F7 Task 7.2 against a real PostgreSQL (Testcontainers + Flyway V13, never
 * H2). Proves what only a real DB can: the review row persists, the {@code (product_id, customer_id)}
 * unique constraint backs the duplicate guard, and a suspended product hides its reviews. The
 * purchase-verification Feign call ({@link OrderClient}) is stubbed so both the fail-closed 503 path
 * and the 403 not-purchased path are exercised without a live order-service.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=review-test",
                "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"order.requested", "payment.failed", "inventory.reserved", "inventory.released"},
        brokerProperties = {
                "auto.create.topics.enable=true",
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@ActiveProfiles("test")
@DisplayName("Review CRUD (integration, Testcontainers PostgreSQL, Feign stubbed)")
class ReviewIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("review_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class NoOpCacheConfig {
        @Bean
        @Primary
        CacheManager noOpCacheManager() {
            return new NoOpCacheManager();
        }
    }

    @MockBean StringRedisTemplate stringRedisTemplate;
    @MockBean RedisConnectionFactory redisConnectionFactory;
    @MockBean OrderClient orderClient;

    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    private static final String TENANT = "test-tenant";
    private static final long CUSTOMER_ID = 42L;

    private int productId;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT);
        Category category = categoryRepository.findAll().get(0); // seeded by Flyway V2
        Product product = productRepository.save(Product.builder()
                .name("Reviewable-Widget").description("has reviews")
                .availableQuantity(10.0).price(BigDecimal.valueOf(25))
                .category(category).tenantId(TENANT).createdBy("9001")
                .status(ProductStatus.ACTIVE).build());
        productId = product.getId();
        authenticateAs(CUSTOMER_ID, "USER");
    }

    @AfterEach
    void tearDown() {
        reviewRepository.deleteAll();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user@test.com", userId, TENANT, role), null, List.of()));
    }

    @Test
    @DisplayName("verified buyer → review persists through the V13 table")
    void verifiedBuyerPersists() {
        when(orderClient.hasPurchased(anyString(), anyInt())).thenReturn(new PurchaseVerificationResponse(true));

        ReviewResponse resp = reviewService.createReview(productId, new ReviewRequest(5, "Excellent"));

        assertThat(resp.id()).isNotNull();
        assertThat(reviewRepository.findById(resp.id())).isPresent()
                .hasValueSatisfying(r -> {
                    assertThat(r.getRating()).isEqualTo(5);
                    assertThat(r.getCustomerId()).isEqualTo(CUSTOMER_ID);
                    assertThat(r.getTenantId()).isEqualTo(TENANT);
                });
    }

    @Test
    @DisplayName("second review by same customer → 409 (unique constraint / existsBy guard)")
    void duplicateRejected() {
        when(orderClient.hasPurchased(anyString(), anyInt())).thenReturn(new PurchaseVerificationResponse(true));
        reviewService.createReview(productId, new ReviewRequest(5, "first"));

        assertThatThrownBy(() -> reviewService.createReview(productId, new ReviewRequest(3, "again")))
                .isInstanceOf(ProductConflictException.class);

        assertThat(reviewRepository.findByProductId(productId, PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("caller never purchased → 403, nothing persisted")
    void notPurchasedForbidden() {
        when(orderClient.hasPurchased(anyString(), anyInt())).thenReturn(new PurchaseVerificationResponse(false));

        assertThatThrownBy(() -> reviewService.createReview(productId, new ReviewRequest(4, null)))
                .isInstanceOf(ProductForbiddenException.class);
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("verification unavailable (circuit open) → 503, nothing persisted")
    void verificationUnavailableFailsClosed() {
        when(orderClient.hasPurchased(anyString(), anyInt())).thenThrow(CallNotPermittedException.class);

        assertThatThrownBy(() -> reviewService.createReview(productId, new ReviewRequest(4, null)))
                .isInstanceOf(ReviewVerificationException.class);
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("getReviews returns the persisted reviews, newest-first page")
    void getReviewsReturnsPage() {
        when(orderClient.hasPurchased(anyString(), anyInt())).thenReturn(new PurchaseVerificationResponse(true));
        reviewService.createReview(productId, new ReviewRequest(4, "solid"));

        assertThat(reviewService.getReviews(productId, PageRequest.of(0, 10)).getContent())
                .extracting(ReviewResponse::rating)
                .containsExactly(4);
    }

    @Test
    @DisplayName("ADMIN deletes another customer's review → removed")
    void adminDeletes() {
        when(orderClient.hasPurchased(anyString(), anyInt())).thenReturn(new PurchaseVerificationResponse(true));
        ReviewResponse created = reviewService.createReview(productId, new ReviewRequest(5, "mine"));

        authenticateAs(999L, "ADMIN");
        reviewService.deleteReview(created.id());

        assertThat(reviewRepository.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("suspended product → getReviews 404 (hidden)")
    void suspendedProductHidesReviews() {
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.SUSPENDED);
        productRepository.save(product);

        assertThatThrownBy(() -> reviewService.getReviews(productId, PageRequest.of(0, 10)))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
