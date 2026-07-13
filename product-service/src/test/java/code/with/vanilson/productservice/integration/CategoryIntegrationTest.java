package code.with.vanilson.productservice.integration;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.category.CategoryRequest;
import code.with.vanilson.productservice.category.CategoryResponse;
import code.with.vanilson.productservice.category.CategoryService;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
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
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CategoryIntegrationTest — Fase 4 Task 4.1 against a real PostgreSQL (Testcontainers +
 * Flyway, never H2) and the full Spring context. Proves the parts that only a real DB
 * can: {@code category.tenant_id NOT NULL} (V4) is satisfied by a runtime-created category,
 * the case-insensitive uniqueness guard, and the referential delete-guard (409 when a
 * product still points at the category).
 * <p>
 * Redis is mocked and the cache manager is {@link NoOpCacheManager} — same recipe as
 * {@link ProductStatusIntegrationTest} (a real Redis container hangs Lettuce on this host).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.config.import=optional:configserver:",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=category-test",
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
@DisplayName("Category CRUD (integration, Testcontainers PostgreSQL)")
class CategoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("category_test")
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

    @Autowired CategoryService categoryService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @BeforeEach
    void ensureSeeded() {
        assertThat(categoryRepository.count())
                .as("Flyway seed categories must exist — migrations must have run in the container")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("create: a runtime category satisfies the NOT NULL tenant_id column and round-trips")
    void createStampsTenantAndPersists() {
        CategoryResponse created = categoryService.createCategory(
                new CategoryRequest("Cable Management", "Cable trays and clips"));

        assertThat(created.id()).isNotNull();
        Category reloaded = categoryRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Cable Management");
        // The NOT NULL tenant_id column (V4) must have been stamped, else the INSERT would fail.
        assertThat(reloaded.getTenantId()).isNotBlank();
        assertThat(categoryService.getAllCategories())
                .extracting(CategoryResponse::name)
                .contains("Cable Management");
    }

    @Test
    @DisplayName("create: duplicate name (case-insensitive vs a seeded category) → 409")
    void createDuplicateNameRejected() {
        // "Keyboards" is seeded by V2; a different case must still collide.
        assertThatThrownBy(() -> categoryService.createCategory(
                new CategoryRequest("keyBOARDS", "dup")))
                .isInstanceOf(ProductConflictException.class)
                .satisfies(ex -> assertThat(((ProductConflictException) ex).getMessageKey())
                        .isEqualTo("category.name.exists"));
    }

    @Test
    @DisplayName("update: renames an existing category and persists")
    void updateRenamesCategory() {
        CategoryResponse created = categoryService.createCategory(
                new CategoryRequest("Temp Name", "temp"));

        CategoryResponse updated = categoryService.updateCategory(
                created.id(), new CategoryRequest("Renamed Category", "now renamed"));

        assertThat(updated.name()).isEqualTo("Renamed Category");
        assertThat(categoryRepository.findById(created.id()).orElseThrow().getName())
                .isEqualTo("Renamed Category");
    }

    @Test
    @DisplayName("update: unknown id → 404")
    void updateUnknownIdIs404() {
        assertThatThrownBy(() -> categoryService.updateCategory(
                999_999, new CategoryRequest("Ghost", "no such row")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("delete: an unreferenced category is removed")
    void deleteUnreferencedCategory() {
        CategoryResponse created = categoryService.createCategory(
                new CategoryRequest("Disposable", "delete me"));

        categoryService.deleteCategory(created.id());

        assertThat(categoryRepository.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("delete: a category still referenced by a product → 409, category untouched")
    void deleteReferencedCategoryRejected() {
        CategoryResponse created = categoryService.createCategory(
                new CategoryRequest("In Use", "has a product"));
        Category category = categoryRepository.findById(created.id()).orElseThrow();

        productRepository.save(Product.builder()
                .name("Referencing-Widget").description("keeps the category alive")
                .availableQuantity(5.0).price(BigDecimal.valueOf(20))
                .category(category).tenantId("default").createdBy("9001")
                .build());

        assertThatThrownBy(() -> categoryService.deleteCategory(created.id()))
                .isInstanceOf(ProductConflictException.class)
                .satisfies(ex -> assertThat(((ProductConflictException) ex).getMessageKey())
                        .isEqualTo("category.delete.has.products"));

        assertThat(categoryRepository.findById(created.id()))
                .as("a rejected delete must leave the category in place")
                .isPresent();
    }
}
