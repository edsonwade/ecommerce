package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductResponse;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.ProductStatus;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.tenantcontext.TenantContext;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductStatusIntegrationTest — Fase 3 Task 3.1: the {@code status} column added by
 * migration {@code V12__add_status_to_product.sql}, against a real PostgreSQL
 * (Testcontainers + Flyway, never H2) and the full Spring context.
 * <p>
 * Proves the three contracts of the task:
 * <ol>
 *   <li>the migration's DB default grandfathers rows inserted WITHOUT a status as ACTIVE
 *       (raw SQL insert bypassing the entity, exactly like every pre-V12 row);</li>
 *   <li>a JPA-persisted entity is born ACTIVE and round-trips;</li>
 *   <li>an explicit SUSPENDED survives the round-trip and reaches the
 *       {@link ProductResponse} returned by the service read path.</li>
 * </ol>
 * Redis is mocked and the cache manager replaced with {@link NoOpCacheManager} — the same
 * recipe as {@link SellerApprovalGuardIntegrationTest} (a real Redis container hangs
 * Lettuce inside the test JVM on this host).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=product-status-test",
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
@DisplayName("Product status column V12 (integration, Testcontainers PostgreSQL)")
class ProductStatusIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("product_status_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Replaces the Redis-backed cache so reads always hit the real DB. */
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
    @Autowired JdbcTemplate jdbcTemplate;

    private Category category;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        category = categoryRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Flyway seed categories missing — migrations did not run in the test container"));
    }

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("V12 DB default: a row inserted without status (pre-V12 shape) reads back ACTIVE")
    void dbDefaultGrandfathersRowsAsActive() {
        // Raw SQL WITHOUT the status column — the exact shape of every pre-V12 insert.
        jdbcTemplate.update(
                "INSERT INTO product (id, name, description, available_quantity, price, category_id, "
                        + "tenant_id, created_by) VALUES (97001, 'Legacy-Row', 'Inserted without status', "
                        + "3.0, 10.00, ?, 'default', '9001')",
                category.getId());

        Product legacy = productRepository.findById(97001).orElseThrow();
        assertThat(legacy.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("JPA persist: a new entity is born ACTIVE and round-trips through the DB")
    void jpaPersistedEntityDefaultsToActive() {
        Product saved = productRepository.save(Product.builder()
                .name("Status-Widget").description("Fase 3 default check")
                .availableQuantity(5.0).price(BigDecimal.valueOf(20))
                .category(category)
                .tenantId("default").createdBy("9001")
                .build());

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    // -------------------------------------------------------
    // Task 3.2 — public read filtering (real DB, real specs)
    // -------------------------------------------------------

    private Product seedActiveAndSuspended() {
        productRepository.save(Product.builder()
                .name("Visible-Widget").description("Active in catalogue")
                .availableQuantity(5.0).price(BigDecimal.valueOf(20))
                .category(category).tenantId("default").createdBy("9001")
                .build());
        return productRepository.save(Product.builder()
                .name("Hidden-Widget").description("Suspended, must vanish from public reads")
                .availableQuantity(5.0).price(BigDecimal.valueOf(30))
                .category(category).tenantId("default").createdBy("9001")
                .status(ProductStatus.SUSPENDED)
                .build());
    }

    private void authAs(long userId, String role, String sellerStatus) {
        var principal = new code.with.vanilson.tenantcontext.security.SecurityPrincipal(
                "it@x.com", userId, "default", role, sellerStatus);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Task 3.2: public list contains the ACTIVE product and NOT the suspended one")
    void publicListHidesSuspended() {
        seedActiveAndSuspended();

        var page = productService.getAllProducts(org.springframework.data.domain.PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(ProductResponse::name)
                .contains("Visible-Widget")
                .doesNotContain("Hidden-Widget");
    }

    @Test
    @DisplayName("Task 3.2: public search does not surface the suspended product")
    void publicSearchHidesSuspended() {
        seedActiveAndSuspended();

        var page = productService.searchProducts("Widget", null, "name", "asc", 0, 50);

        assertThat(page.getContent())
                .extracting(ProductResponse::name)
                .contains("Visible-Widget")
                .doesNotContain("Hidden-Widget");
    }

    @Test
    @DisplayName("Task 3.2: anonymous detail of a suspended product → 404 (no existence leak)")
    void anonymousSuspendedDetail404() {
        Product hidden = seedActiveAndSuspended();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> productService.getProductById(hidden.getId()))
                .isInstanceOf(code.with.vanilson.productservice.exception.ProductNotFoundException.class);
    }

    @Test
    @DisplayName("Task 3.2: another seller's detail of a suspended product → 404")
    void otherSellerSuspendedDetail404() {
        Product hidden = seedActiveAndSuspended();
        authAs(8888L, "SELLER", "APPROVED");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> productService.getProductById(hidden.getId()))
                .isInstanceOf(code.with.vanilson.productservice.exception.ProductNotFoundException.class);
    }

    @Test
    @DisplayName("Task 3.2: the owner's 'my products' listing still shows the suspended product")
    void ownerMyProductsShowsSuspended() {
        seedActiveAndSuspended();
        authAs(9001L, "SELLER", "APPROVED");

        var page = productService.getMyProducts(org.springframework.data.domain.PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(ProductResponse::name)
                .contains("Visible-Widget", "Hidden-Widget");
    }

    @Test
    @DisplayName("Task 3.2: ADMIN detail of a suspended product still works")
    void adminSuspendedDetailVisible() {
        Product hidden = seedActiveAndSuspended();
        authAs(1L, "ADMIN", null);

        ProductResponse response = productService.getProductById(hidden.getId());

        assertThat(response.status()).isEqualTo(ProductStatus.SUSPENDED);
    }

    // -------------------------------------------------------
    // Task 3.3 — purchase-path rejection (real DB, real TX)
    // -------------------------------------------------------

    @Test
    @DisplayName("Task 3.3: purchasing a SUSPENDED product → ProductPurchaseException(product.suspended), stock untouched")
    void purchaseOfSuspendedProductRejectedAndStockUntouched() {
        Product hidden = seedActiveAndSuspended();
        double stockBefore = hidden.getAvailableQuantity();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        productService.purchaseProducts(java.util.List.of(
                                new code.with.vanilson.productservice.ProductPurchaseRequest(
                                        hidden.getId(), 1.0))))
                .isInstanceOf(code.with.vanilson.productservice.exception.ProductPurchaseException.class)
                .satisfies(ex -> assertThat(
                        ((code.with.vanilson.productservice.exception.ProductPurchaseException) ex)
                                .getProductId()).isEqualTo(hidden.getId()));

        // The whole reservation TX must have rolled back — stock unchanged in the DB.
        assertThat(productRepository.findById(hidden.getId()).orElseThrow().getAvailableQuantity())
                .isEqualTo(stockBefore);
    }

    // -------------------------------------------------------
    // Task 3.4 — admin status management (real DB, real cache eviction path)
    // -------------------------------------------------------

    @Test
    @DisplayName("Task 3.4: suspend → vanishes from public list; reactivate → visible again")
    void suspendReactivateFullCycle() {
        authAs(1L, "ADMIN", null);
        Product product = productRepository.save(Product.builder()
                .name("Cycle-Widget").description("Suspend/reactivate cycle")
                .availableQuantity(5.0).price(BigDecimal.valueOf(20))
                .category(category).tenantId("default").createdBy("9001")
                .build());
        var page = org.springframework.data.domain.PageRequest.of(0, 50);

        // Born ACTIVE → in the public list.
        SecurityContextHolder.clearContext();
        assertThat(productService.getAllProducts(page).getContent())
                .extracting(ProductResponse::name).contains("Cycle-Widget");

        // ADMIN suspends → gone from the public list, status persisted.
        authAs(1L, "ADMIN", null);
        ProductResponse suspended = productService.updateProductStatus(
                product.getId(), ProductStatus.SUSPENDED);
        assertThat(suspended.status()).isEqualTo(ProductStatus.SUSPENDED);

        SecurityContextHolder.clearContext();
        assertThat(productService.getAllProducts(page).getContent())
                .extracting(ProductResponse::name).doesNotContain("Cycle-Widget");

        // ADMIN reactivates → visible again.
        authAs(1L, "ADMIN", null);
        ProductResponse reactivated = productService.updateProductStatus(
                product.getId(), ProductStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ProductStatus.ACTIVE);

        SecurityContextHolder.clearContext();
        assertThat(productService.getAllProducts(page).getContent())
                .extracting(ProductResponse::name).contains("Cycle-Widget");
    }

    @Test
    @DisplayName("Task 3.4: admin list returns every status; public list only ACTIVE")
    void adminListReturnsAllStatuses() {
        seedActiveAndSuspended();
        authAs(1L, "ADMIN", null);
        var page = org.springframework.data.domain.PageRequest.of(0, 50);

        assertThat(productService.getAllProductsForAdmin(page).getContent())
                .extracting(ProductResponse::name)
                .contains("Visible-Widget", "Hidden-Widget");
    }

    @Test
    @DisplayName("SUSPENDED round-trips and reaches the ProductResponse on the owner's read path")
    void suspendedStatusReachesTheResponse() {
        // Read as the OWNER (seller 9001): Task 3.2 will hide suspended products from
        // everyone else, but the owner keeps seeing their own — this test stays green.
        var principal = new code.with.vanilson.tenantcontext.security.SecurityPrincipal(
                "it@x.com", 9001L, "default", "SELLER", "APPROVED");
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SELLER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Product suspended = Product.builder()
                .name("Suspended-Widget").description("Fase 3 suspended check")
                .availableQuantity(5.0).price(BigDecimal.valueOf(20))
                .category(category)
                .tenantId("default").createdBy("9001")
                .status(ProductStatus.SUSPENDED)
                .build();
        Product saved = productRepository.save(suspended);

        ProductResponse response = productService.getProductById(saved.getId());

        assertThat(response.status()).isEqualTo(ProductStatus.SUSPENDED);
    }
}
