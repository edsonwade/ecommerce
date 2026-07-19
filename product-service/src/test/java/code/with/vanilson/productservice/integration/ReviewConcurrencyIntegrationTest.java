package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductStatus;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.review.OrderClient;
import code.with.vanilson.productservice.review.PurchaseVerificationResponse;
import code.with.vanilson.productservice.review.ReviewRepository;
import code.with.vanilson.productservice.review.ReviewRequest;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ReviewConcurrencyIntegrationTest — F7 confirmation C4, required explicitly by the plan.
 * <p>
 * {@code N} customers review the SAME product at the same instant (released together by a
 * {@link CountDownLatch}), against a real PostgreSQL (Testcontainers, never H2 — an in-memory DB
 * would not reproduce the row-lock this proves).
 * <p>
 * What it proves that a mock never could: {@code ReviewService.refreshProductRating} takes the
 * product row-lock as a <em>separate statement</em> ({@code lockProductForRatingRecompute}) before
 * the recompute-from-source UPDATE, so a blocked writer resumes into a fresh READ COMMITTED snapshot
 * in which the previous writer's review row is visible. The last committer therefore writes the
 * correct aggregate — no lost updates, no drift, and no version column.
 * <p>
 * This test caught the version where the lock lived <em>inside</em> the UPDATE: PostgreSQL re-checked
 * only the locked product row against the newer snapshot and kept the statement's original snapshot
 * for the {@code product_review} sub-SELECTs, so 8 simultaneous reviews recorded a review_count of 3.
 * A naive {@code count = count + 1} would fail here too.
 * <p>
 * It then caught the fix's own regression: the split-out lock was first written as {@code FOR UPDATE},
 * which conflicts with the {@code FOR KEY SHARE} lock the review INSERT already holds on the same
 * product row via its foreign key — 8 reviewers deadlocked and 6 were aborted. The lock must be
 * {@code FOR NO KEY UPDATE}. Both regressions are invisible to a mock and to a single-threaded test.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=review-concurrency-test",
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
@DisplayName("Rating counters under real concurrency (C4, Testcontainers PostgreSQL)")
class ReviewConcurrencyIntegrationTest {

    /** Concurrent reviewers. Ratings alternate 5 / 4, so the expected average is exactly 4.5. */
    private static final int REVIEWERS = 8;

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("review_concurrency_test")
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

    private int productId;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT);
        Category category = categoryRepository.findAll().get(0); // seeded by Flyway V2
        productId = productRepository.save(Product.builder()
                .name("Hot-Widget").description("everyone reviews it at once")
                .availableQuantity(100.0).price(BigDecimal.valueOf(25))
                .category(category).tenantId(TENANT).createdBy("9001")
                .status(ProductStatus.ACTIVE).build()).getId();
        when(orderClient.hasPurchased(anyString(), anyInt())).thenReturn(new PurchaseVerificationResponse(true));
        TenantContext.clear(); // each worker thread binds its own; see runReviewer
    }

    @AfterEach
    void tearDown() {
        reviewRepository.deleteAll();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    /**
     * One reviewer, on its own thread. TenantContext and SecurityContextHolder are ThreadLocals, so
     * every worker must bind (and clear) its own — inheriting the test thread's would not happen.
     */
    private void runReviewer(long customerId, int rating) {
        try {
            TenantContext.setCurrentTenantId(TENANT);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new SecurityPrincipal("user" + customerId + "@test.com", customerId, TENANT, "USER"),
                            null, List.of()));
            reviewService.createReview(productId, new ReviewRequest(rating, "concurrent-" + customerId));
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("N simultaneous reviews → review_count == N and average is exact (row-lock + recompute)")
    void concurrentReviewsKeepCountersExact() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REVIEWERS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(REVIEWERS);
        // Failures are collected WITH their cause, not counted. A bare count told us only that
        // "6 of 8 reviewers failed" and hid the actual reason (a PostgreSQL deadlock between the
        // FK's FOR KEY SHARE lock and the recompute's row-lock) behind three diagnosis rounds.
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < REVIEWERS; i++) {
                long customerId = 100L + i;
                int rating = (i % 2 == 0) ? 5 : 4;
                pool.submit(() -> {
                    try {
                        startGate.await();          // release every thread at the same instant
                        runReviewer(customerId, rating);
                    } catch (Exception ex) {
                        failures.add(ex);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startGate.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS))
                    .as("all concurrent reviewers should finish (a deadlock would hang here)")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures)
                .as("no reviewer should fail — a CannotAcquireLockException here means the recompute "
                        + "row-lock conflicts with the review INSERT's foreign-key FOR KEY SHARE lock")
                .extracting(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
                .isEmpty();
        assertThat(reviewRepository.count()).isEqualTo(REVIEWERS);

        TenantContext.setCurrentTenantId(TENANT);
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getReviewCount())
                .as("recompute-from-source under a row-lock must not lose a single concurrent write")
                .isEqualTo(REVIEWERS);
        assertThat(product.getAverageRating())
                .as("4 ratings of 5 and 4 of 4 → exactly 4.5")
                .isEqualByComparingTo("4.5");
    }
}
