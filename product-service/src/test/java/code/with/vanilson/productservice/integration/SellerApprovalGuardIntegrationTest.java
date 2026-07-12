package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

/**
 * SellerApprovalGuardIntegrationTest — Fase 2 seller approval write-guard against a real
 * PostgreSQL (Testcontainers + Flyway, never H2) and the full Spring context.
 * <p>
 * The guard reads the {@code sellerStatus} field of the {@link SecurityPrincipal} placed
 * in the SecurityContext by the shared tenant-context JWT filter. Here the principal is
 * set directly (same technique as the ownership tests) so every status combination can
 * be exercised without minting real tokens:
 * PENDING_APPROVAL / SUSPENDED → 403 with the proper key; APPROVED / null (old token,
 * grandfathered seller) → writes proceed; ADMIN never gated.
 * <p>
 * Redis is mocked and the cache manager replaced with {@link NoOpCacheManager} — the
 * same recipe as {@link ProductTenantIsolationIntegrationTest} (a real Redis container
 * hangs Lettuce inside the test JVM on this host).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=seller-approval-guard-test",
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
                // Cap internal-topic index preallocation — without these the embedded
                // broker preallocates ~2.3 GB of index files per run in %TEMP%.
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@ActiveProfiles("test")
@DisplayName("Seller approval write-guard (integration, Testcontainers PostgreSQL)")
class SellerApprovalGuardIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("seller_approval_guard_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Replaces the Redis-backed cache so writes always hit the real DB. */
    @TestConfiguration
    static class NoOpCacheConfig {
        @Bean
        @Primary
        CacheManager noOpCacheManager() {
            return new NoOpCacheManager();
        }
    }

    // No live Redis in this context — same recipe as the other product integration tests.
    @MockBean StringRedisTemplate stringRedisTemplate;
    @MockBean RedisConnectionFactory redisConnectionFactory;

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    private Category category;
    private Product ownProduct;

    @BeforeEach
    void seedOwnProduct() {
        productRepository.deleteAll();
        category = categoryRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Flyway seed categories missing — migrations did not run in the test container"));

        // A product already owned by seller 9001 — the update/delete targets.
        ownProduct = productRepository.save(Product.builder()
                .name("Guarded-Widget").description("Owned by seller 9001")
                .availableQuantity(10.0).price(BigDecimal.valueOf(50))
                .category(category)
                .tenantId("default").createdBy("9001")
                .build());
    }

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        productRepository.deleteAll();
    }

    private void authAs(long userId, String role, String sellerStatus) {
        var principal = new SecurityPrincipal("it@x.com", userId, "default", role, sellerStatus);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Product freshProduct() {
        return Product.builder()
                .name("New-Widget").description("Fresh product")
                .availableQuantity(5.0).price(BigDecimal.valueOf(20))
                .category(category)
                .build();
    }

    @Test
    @DisplayName("PENDING_APPROVAL seller: create is rejected 403 and nothing is persisted")
    void pendingSellerCreateRejected() {
        authAs(9001L, "SELLER", "PENDING_APPROVAL");
        long before = productRepository.count();

        assertThatThrownBy(() -> productService.createProduct(freshProduct()))
                .isInstanceOf(ProductForbiddenException.class)
                .satisfies(ex -> assertThat(((ProductForbiddenException) ex).getMessageKey())
                        .isEqualTo("seller.not.approved"));

        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("SUSPENDED seller: update of their OWN product is rejected 403")
    void suspendedSellerUpdateRejected() {
        authAs(9001L, "SELLER", "SUSPENDED");

        Product update = new Product(ownProduct.getId(), "Tampered", "Desc", 5.0, BigDecimal.ONE);

        assertThatThrownBy(() -> productService.updateProduct(ownProduct.getId(), update))
                .isInstanceOf(ProductForbiddenException.class)
                .satisfies(ex -> assertThat(((ProductForbiddenException) ex).getMessageKey())
                        .isEqualTo("seller.suspended"));

        assertThat(productRepository.findById(ownProduct.getId()))
                .get()
                .extracting(Product::getName)
                .isEqualTo("Guarded-Widget");
    }

    @Test
    @DisplayName("APPROVED seller: create persists normally")
    void approvedSellerCreatePersists() {
        authAs(9001L, "SELLER", "APPROVED");

        productService.createProduct(freshProduct());

        assertThat(productRepository.findAll())
                .extracting(Product::getName)
                .contains("New-Widget");
    }

    @Test
    @DisplayName("old-token seller (null claim): create still works — grandfathered compat")
    void nullClaimSellerCreateStillWorks() {
        authAs(9001L, "SELLER", null);

        productService.createProduct(freshProduct());

        assertThat(productRepository.findAll())
                .extracting(Product::getName)
                .contains("New-Widget");
    }

    @Test
    @DisplayName("ADMIN: never gated by seller status — update of any product works")
    void adminNeverGated() {
        authAs(1L, "ADMIN", null);

        Product update = new Product(ownProduct.getId(), "Admin-Renamed", "Desc",
                10.0, BigDecimal.valueOf(50));
        productService.updateProduct(ownProduct.getId(), update);

        assertThat(productRepository.findById(ownProduct.getId()))
                .get()
                .extracting(Product::getName)
                .isEqualTo("Admin-Renamed");
    }

    @Test
    @DisplayName("SUSPENDED seller: delete of their OWN product is rejected and the row survives")
    void suspendedSellerDeleteRejected() {
        authAs(9001L, "SELLER", "SUSPENDED");

        assertThatThrownBy(() -> productService.deleteProduct(ownProduct.getId()))
                .isInstanceOf(ProductForbiddenException.class);

        assertThat(productRepository.findById(ownProduct.getId())).isPresent();
    }
}
