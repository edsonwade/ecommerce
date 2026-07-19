package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductResponse;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.ProductStatus;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.review.OrderClient;
import code.with.vanilson.productservice.review.PurchaseVerificationResponse;
import code.with.vanilson.productservice.review.ReviewRepository;
import code.with.vanilson.productservice.review.ReviewRequest;
import code.with.vanilson.productservice.review.ReviewResponse;
import code.with.vanilson.productservice.review.ReviewService;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * CatalogRatingIntegrationTest — F7 Task 7.3 (Decision A1) against a real PostgreSQL
 * (Testcontainers + Flyway V14, never H2).
 * <p>
 * Proves the denormalised counters end-to-end through the real recompute-from-source UPDATE: they
 * land on the product row after every review write and delete, they surface on the catalogue's
 * {@link ProductResponse}, and a product with no reviews reads 0.0 / 0 (never null — the columns are
 * NOT NULL). Rounding to one decimal is asserted too, since it is done by the database
 * ({@code ROUND(AVG(rating), 1)}) and no unit test can prove that.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=catalog-rating-test",
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
@DisplayName("Catalog rating counters (integration, Testcontainers PostgreSQL)")
class CatalogRatingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("catalog_rating_test")
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
            // Caching is deliberately out of the picture here: this test is about the DB-side
            // recompute. The cache policy (evict detail / keep list) is proven in ReviewServiceTest.
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
    @Autowired ProductService productService;

    private static final String TENANT = "test-tenant";

    private int productId;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT);
        Category category = categoryRepository.findAll().get(0); // seeded by Flyway V2
        Product product = productRepository.save(Product.builder()
                .name("Rated-Widget").description("collects stars")
                .availableQuantity(10.0).price(BigDecimal.valueOf(25))
                .category(category).tenantId(TENANT).createdBy("9001")
                .status(ProductStatus.ACTIVE).build());
        productId = product.getId();
        // Every customer in this test is a verified buyer; the purchase guard is covered elsewhere.
        when(orderClient.hasPurchased(anyString(), anyInt())).thenReturn(new PurchaseVerificationResponse(true));
    }

    @AfterEach
    void tearDown() {
        reviewRepository.deleteAll();
        productRepository.deleteById(productId);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticateAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user" + userId + "@test.com", userId, TENANT, "USER"), null, List.of()));
    }

    /** One review per customer (unique constraint), so each rating comes from a different user. */
    private ReviewResponse review(long customerId, int rating) {
        authenticateAs(customerId);
        return reviewService.createReview(productId, new ReviewRequest(rating, "r" + rating));
    }

    private Product reloadProduct() {
        return productRepository.findById(productId).orElseThrow();
    }

    @Test
    @DisplayName("no reviews → product reads 0.0 / 0 (NOT NULL defaults from V14)")
    void zeroReviewsReadsZero() {
        Product product = reloadProduct();

        assertThat(product.getAverageRating()).isEqualByComparingTo("0.0");
        assertThat(product.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("first review → counters recomputed on the product row")
    void firstReviewSetsCounters() {
        review(42L, 5);

        Product product = reloadProduct();
        assertThat(product.getAverageRating()).isEqualByComparingTo("5.0");
        assertThat(product.getReviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("several reviews → average is the true mean, rounded to one decimal by the DB")
    void averageIsRoundedToOneDecimal() {
        review(42L, 5);
        review(43L, 4);
        review(44L, 4);

        // (5 + 4 + 4) / 3 = 4.333... → ROUND(...,1) = 4.3
        Product product = reloadProduct();
        assertThat(product.getAverageRating()).isEqualByComparingTo("4.3");
        assertThat(product.getReviewCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("deleting a review recalculates the average and the count")
    void deleteRecalculates() {
        review(42L, 5);
        ReviewResponse toRemove = review(43L, 1);
        assertThat(reloadProduct().getAverageRating()).isEqualByComparingTo("3.0"); // (5+1)/2

        authenticateAs(43L); // the review's own author may delete it
        reviewService.deleteReview(toRemove.id());

        Product product = reloadProduct();
        assertThat(product.getAverageRating()).isEqualByComparingTo("5.0");
        assertThat(product.getReviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleting the LAST review falls back to 0.0 / 0, not null (COALESCE guard)")
    void deletingLastReviewResetsToZero() {
        ReviewResponse only = review(42L, 4);

        authenticateAs(42L);
        reviewService.deleteReview(only.id());

        Product product = reloadProduct();
        assertThat(product.getAverageRating())
                .as("AVG over zero rows is NULL in SQL; COALESCE must bring it back to 0 for the NOT NULL column")
                .isEqualByComparingTo("0.0");
        assertThat(product.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("counters surface on the catalogue ProductResponse (detail + list)")
    void countersSurfaceOnCatalogResponse() {
        review(42L, 5);
        review(43L, 4);

        ProductResponse detail = productService.getProductById(productId);
        assertThat(detail.averageRating()).isEqualByComparingTo("4.5");
        assertThat(detail.reviewCount()).isEqualTo(2);

        ProductResponse fromList = productService
                .searchProducts("Rated-Widget", null, "name", "asc", 0, 10)
                .getContent().get(0);
        assertThat(fromList.averageRating()).isEqualByComparingTo("4.5");
        assertThat(fromList.reviewCount()).isEqualTo(2);
    }
}
