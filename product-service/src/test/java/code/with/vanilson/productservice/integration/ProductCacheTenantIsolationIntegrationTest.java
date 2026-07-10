package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductResponse;
import code.with.vanilson.productservice.ProductService;
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
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductCacheTenantIsolationIntegrationTest — B3 Fase 1b (cache-key tenant isolation)
 * <p>
 * Companion to {@link ProductTenantIsolationIntegrationTest}, which runs with a
 * {@code NoOpCacheManager} to prove the <em>database</em> filter in isolation. This test
 * instead wires a real, in-memory {@link ConcurrentMapCacheManager} so the {@code @Cacheable}
 * key is actually exercised: it proves that a value cached under one tenant is never served
 * to another.
 * <p>
 * Before Fase 1b the {@code product-list} key was {@code catalogScopeKey()+page+size} and the
 * {@code products} key was just {@code #id} — both tenant-blind. With a shared cache that meant
 * tenant A's first read populated an entry that tenant B would then be served verbatim, a leak
 * that survives even a correct DB filter (the DB is never hit on a cache hit). The keys now carry
 * {@code cacheTenantKey()}, so each tenant has its own entry.
 * <p>
 * A real Redis is intentionally avoided (Lettuce hangs against a container on this host — same
 * reason as the sibling test); the in-memory cache manager exercises the same key SpEL.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=product-cache-tenant-isolation-test",
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
@DisplayName("Tenant isolation — product catalogue cache key (integration, real in-memory cache)")
class ProductCacheTenantIsolationIntegrationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("product_cache_tenant_isolation_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Real, in-memory cache so the @Cacheable key is actually evaluated and stored. */
    @TestConfiguration
    static class RealCacheConfig {
        @Bean
        @Primary
        CacheManager inMemoryCacheManager() {
            return new ConcurrentMapCacheManager("products", "product-list");
        }
    }

    @MockBean StringRedisTemplate stringRedisTemplate;
    @MockBean RedisConnectionFactory redisConnectionFactory;

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Product laptopA;

    @BeforeEach
    void seedTwoTenants() {
        tx = new TransactionTemplate(transactionManager);
        productRepository.deleteAll();

        // ProductMapper.fromProduct rejects a product without a category, so the seed
        // must carry one or every service read fails before the cache key is exercised.
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
        productRepository.save(Product.builder()
                .name("A-Phone").description("Tenant A phone")
                .availableQuantity(20.0).price(BigDecimal.valueOf(800))
                .category(category)
                .tenantId(TENANT_A).createdBy("9001")
                .build());
        productRepository.save(Product.builder()
                .name("B-Camera").description("Tenant B camera")
                .availableQuantity(5.0).price(BigDecimal.valueOf(600))
                .category(category)
                .tenantId(TENANT_B).createdBy("9002")
                .build());
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("a catalogue page cached under tenant A is not served to tenant B")
    void catalogueListCacheDoesNotLeakAcrossTenants() {
        // Tenant A reads first — populates the product-list cache under its tenant key.
        TenantContext.setCurrentTenantId(TENANT_A);
        Page<ProductResponse> tenantAPage = tx.execute(status ->
                productService.getAllProducts(PageRequest.of(0, 50)));
        assertThat(tenantAPage.getContent())
                .extracting(ProductResponse::name)
                .containsExactlyInAnyOrder("A-Laptop", "A-Phone");

        // Tenant B reads the same page/size — a tenant-blind key would serve A's cached list.
        TenantContext.setCurrentTenantId(TENANT_B);
        Page<ProductResponse> tenantBPage = tx.execute(status ->
                productService.getAllProducts(PageRequest.of(0, 50)));

        assertThat(tenantBPage.getContent())
                .as("tenant B must get its own cache entry, never tenant A's cached page")
                .extracting(ProductResponse::name)
                .containsExactly("B-Camera")
                .doesNotContain("A-Laptop", "A-Phone");
    }

    @Test
    @DisplayName("a product cached by id under tenant A is not served to tenant B")
    void productByIdCacheDoesNotLeakAcrossTenants() {
        // Tenant A caches its product under the tenant-scoped id key.
        TenantContext.setCurrentTenantId(TENANT_A);
        ProductResponse cached = tx.execute(status -> productService.getProductById(laptopA.getId()));
        assertThat(cached.name()).isEqualTo("A-Laptop");

        // Tenant B asking for the SAME id must not be served A's cached entry — it is a 404.
        TenantContext.setCurrentTenantId(TENANT_B);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        tx.execute(status -> productService.getProductById(laptopA.getId())))
                .as("tenant B must not read tenant A's product from a shared-by-id cache entry")
                .isInstanceOf(code.with.vanilson.productservice.exception.ProductNotFoundException.class);
    }
}
