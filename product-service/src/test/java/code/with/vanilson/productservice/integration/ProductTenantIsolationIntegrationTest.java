package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductResponse;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProductTenantIsolationIntegrationTest — B3 Fase 1 (cross-tenant isolation, tests first)
 * <p>
 * Creates products under TWO different tenants in a real PostgreSQL (Testcontainers,
 * never H2) and asserts that reads scoped to one tenant return ZERO rows of the other.
 * This is the isolation proof that never existed: all live users share
 * {@code tenantId="default"}, so the tenant substrate has never been exercised.
 * <p>
 * Two test groups with different intent:
 * <ul>
 *   <li><b>Substrate</b> — proves the {@code @FilterDef}/{@code @Filter} +
 *       {@link TenantHibernateFilterActivator} machinery filters correctly when
 *       explicitly activated inside a transaction. Expected GREEN today.</li>
 *   <li><b>Service reads</b> — encodes the REQUIRED behaviour of the public
 *       {@link ProductService} read path. Expected RED today: ProductService never
 *       calls {@code activateFilter()} on any read, and {@code getProductById} uses
 *       {@code repository.findById} (= {@code em.find}), which Hibernate filters do
 *       not apply to by design. A red run here is the documented evidence of the
 *       cross-tenant leak that B3 Fase 1b must fix.</li>
 * </ul>
 * <p>
 * Queries run inside a {@link TransactionTemplate} to mirror production semantics:
 * with OSIV a request shares one Hibernate session, so a filter enabled at the start
 * of the request applies to its queries. Outside a transaction the shared
 * EntityManager proxy would hand the activator a throwaway session and the test
 * would pass/fail for the wrong reason.
 * <p>
 * Redis is mocked and the cache manager replaced with {@link NoOpCacheManager}:
 * the L2 cache is not what this test proves, and on this host a real Redis container
 * hangs Lettuce inside the test JVM (same pattern as the Prometheus scrape test).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=product-tenant-isolation-test",
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
@DisplayName("Tenant isolation — product-service (integration, Testcontainers PostgreSQL)")
class ProductTenantIsolationIntegrationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("product_tenant_isolation_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Replaces the Redis-backed cache so @Cacheable reads always hit the real DB. */
    @TestConfiguration
    static class NoOpCacheConfig {
        @Bean
        @Primary
        CacheManager noOpCacheManager() {
            return new NoOpCacheManager();
        }
    }

    // No live Redis in this context — same recipe as ProductPrometheusScrapeIntegrationTest.
    @MockBean StringRedisTemplate stringRedisTemplate;
    @MockBean RedisConnectionFactory redisConnectionFactory;

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TenantHibernateFilterActivator filterActivator;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    private Product laptopA;
    private Product phoneA;
    private Product cameraB;

    @BeforeEach
    void seedTwoTenants() {
        tx = new TransactionTemplate(transactionManager);

        // Flyway seed products (createdBy="system") would pollute the assertions.
        productRepository.deleteAll();

        // ProductMapper.fromProduct rejects a product without a category, so the seed
        // must carry one or every service read fails before the tenant filter is exercised.
        // Reuse a Flyway-seeded category: category.tenant_id is NOT NULL (V4) but the JPA
        // entity does not map it — in production categories are only ever inserted by
        // migrations, so saving one through the repository violates the constraint.
        Category category = categoryRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Flyway seed categories missing — migrations did not run in the test container"));

        laptopA = productRepository.save(Product.builder()
                .name("A-Laptop").description("Tenant A laptop")
                .availableQuantity(10.0).price(BigDecimal.valueOf(1500))
                .category(category)
                .tenantId(TENANT_A).createdBy("9001")
                .build());
        phoneA = productRepository.save(Product.builder()
                .name("A-Phone").description("Tenant A phone")
                .availableQuantity(20.0).price(BigDecimal.valueOf(800))
                .category(category)
                .tenantId(TENANT_A).createdBy("9001")
                .build());
        cameraB = productRepository.save(Product.builder()
                .name("B-Camera").description("Tenant B camera")
                .availableQuantity(5.0).price(BigDecimal.valueOf(600))
                .category(category)
                .tenantId(TENANT_B).createdBy("9002")
                .build());
    }

    @AfterEach
    void clearTenantAndData() {
        TenantContext.clear();
        productRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group 1 — substrate: proves the filter machinery works when activated.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Substrate — Hibernate tenant filter, explicitly activated")
    class Substrate {

        @Test
        @DisplayName("tenant A sees only its 2 products, zero rows of tenant B")
        void tenantASeesOnlyItsOwnRows() {
            TenantContext.setCurrentTenantId(TENANT_A);

            List<Product> visible = tx.execute(status -> {
                filterActivator.activateFilter();
                return productRepository.findAll();
            });

            assertThat(visible)
                    .as("with tenantFilter active, tenant A must see exactly its own products")
                    .extracting(Product::getName)
                    .containsExactlyInAnyOrder("A-Laptop", "A-Phone")
                    .doesNotContain("B-Camera");
        }

        @Test
        @DisplayName("tenant B sees only its 1 product, zero rows of tenant A")
        void tenantBSeesOnlyItsOwnRows() {
            TenantContext.setCurrentTenantId(TENANT_B);

            List<Product> visible = tx.execute(status -> {
                filterActivator.activateFilter();
                return productRepository.findAll();
            });

            assertThat(visible)
                    .as("with tenantFilter active, tenant B must see exactly its own products")
                    .extracting(Product::getName)
                    .containsExactly("B-Camera");
        }

        @Test
        @DisplayName("without tenant context the filter is a no-op and all rows are visible (dev behaviour)")
        void noTenantContextMeansNoFiltering() {
            // TenantContext deliberately NOT set — activateFilter() must no-op.
            List<Product> visible = tx.execute(status -> {
                filterActivator.activateFilter();
                return productRepository.findAll();
            });

            assertThat(visible)
                    .as("no tenant context → unfiltered read (single-tenant dev keeps working)")
                    .hasSize(3);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group 2 — REQUIRED service behaviour. RED today = documented leak:
    // ProductService reads never call activateFilter(), and findById bypasses
    // Hibernate filters entirely. Fase 1b makes these green.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Service reads — required tenant isolation (RED today = cross-tenant leak)")
    class ServiceReads {

        @Test
        @DisplayName("getAllProducts under tenant A must return zero rows of tenant B")
        void getAllProductsMustBeTenantScoped() {
            TenantContext.setCurrentTenantId(TENANT_A);

            Page<ProductResponse> page = tx.execute(status ->
                    productService.getAllProducts(PageRequest.of(0, 50)));

            assertThat(page.getContent())
                    .as("catalogue read under tenant A must not leak tenant B products")
                    .extracting(ProductResponse::name)
                    .containsExactlyInAnyOrder("A-Laptop", "A-Phone")
                    .doesNotContain("B-Camera");
        }

        @Test
        @DisplayName("getAllProducts under tenant B must return zero rows of tenant A")
        void getAllProductsMustBeTenantScopedSymmetric() {
            TenantContext.setCurrentTenantId(TENANT_B);

            Page<ProductResponse> page = tx.execute(status ->
                    productService.getAllProducts(PageRequest.of(0, 50)));

            assertThat(page.getContent())
                    .as("catalogue read under tenant B must not leak tenant A products")
                    .extracting(ProductResponse::name)
                    .containsExactly("B-Camera");
        }

        @Test
        @DisplayName("getProductById across tenants must behave as not-found")
        void getProductByIdMustNotCrossTenants() {
            TenantContext.setCurrentTenantId(TENANT_A);

            assertThatThrownBy(() -> tx.execute(status ->
                    productService.getProductById(cameraB.getId())))
                    .as("tenant A reading tenant B's product by id must be a 404, not a leak")
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("getProductById within the same tenant still works (positive control)")
        void getProductByIdSameTenantStillWorks() {
            TenantContext.setCurrentTenantId(TENANT_A);

            ProductResponse response = tx.execute(status ->
                    productService.getProductById(laptopA.getId()));

            assertThat(response.name()).isEqualTo("A-Laptop");
        }
    }
}
