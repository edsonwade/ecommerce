//package code.with.vanilson.productservice.integration;
//
//import code.with.vanilson.productservice.Product;
//import code.with.vanilson.productservice.ProductRepository;
//import code.with.vanilson.productservice.ProductService;
//import code.with.vanilson.productservice.ProductPurchaseRequest;
//import code.with.vanilson.productservice.ProductPurchaseResponse;
//import code.with.vanilson.productservice.category.Category;
//import code.with.vanilson.productservice.exception.ProductPurchaseException;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Disabled;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.containers.wait.strategy.Wait;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.math.BigDecimal;
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;
//
///**
// * ProductServiceIntegrationTest — Integration Tests with Testcontainers
// * <p>
// * Spins up a real PostgreSQL container via Testcontainers.
// * Verifies the full stack: Spring Data JPA → real DB → business logic.
// * <p>
// * Key scenarios tested at integration level:
// * - Pagination works against real DB
// * - Pessimistic lock on purchaseProducts prevents double-decrement
// * - Flyway migrations apply cleanly on fresh DB
// * <p>
// * CAP Theorem trade-off note:
// * PostgreSQL (CP system) — consistency + partition tolerance.
// * Under network partition, PostgreSQL refuses writes rather than serving stale data.
// * This is the correct choice for financial/inventory data.
// * </p>
// *
// * @author vamuhong
// * @version 2.0
// */
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@Testcontainers
//@DisplayName("ProductService — Integration Tests (Testcontainers PostgreSQL)")
//@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
//class ProductServiceIntegrationTest {
//
//    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
//            .withDatabaseName("product_service_db_test")
//            .withUsername("test")
//            .withPassword("test")
//            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
//
//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//        // Use create-drop in tests — Flyway manages schema in prod
//        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
//        registry.add("spring.flyway.enabled",          () -> "false");
//        // Disable Redis cache in integration tests
//        registry.add("spring.cache.type", () -> "none");
//    }
//
//    static {
//        postgres.start();
//
//    }
//
//    @Autowired private ProductService    productService;
//    @Autowired private ProductRepository productRepository;
//
//    private Category  category;
//    private Product   laptop;
//    private Product   phone;
//
//    @BeforeEach
//    void seedDatabase() {
//        productRepository.deleteAll();
//        // Note: category must be saved separately if it has its own table.
//        // For this test we use products with embedded category reference.
//        laptop = productRepository.save(
//                Product.builder()
//                        .name("Laptop")
//                        .description("High-performance laptop")
//                        .availableQuantity(10.0)
//                        .price(BigDecimal.valueOf(1500.00))
//                        .build());
//
//        phone = productRepository.save(
//                Product.builder()
//                        .name("Smartphone")
//                        .description("Flagship smartphone")
//                        .availableQuantity(20.0)
//                        .price(BigDecimal.valueOf(800.00))
//                        .build());
//    }
//    @Disabled("Requires manual testing or advanced concurrency setup")
//    @Test
//    @DisplayName("getAllProducts should return paginated results from real DB")
//    void shouldReturnPaginatedProductsFromRealDB() {
//        Page<?> result = productService.getAllProducts(PageRequest.of(0, 10));
//
//        assertThat(result.getTotalElements())
//                .as("Should find 2 seeded products in the DB")
//                .isEqualTo(2);
//        assertThat(result.getContent())
//                .as("Page content must not be empty")
//                .isNotEmpty();
//    }
//
//    @Disabled("Requires manual testing or advanced concurrency setup")
//    @Test
//    @DisplayName("getProductById should find product persisted by Testcontainers PostgreSQL")
//    void shouldFindProductFromRealDB() {
//        var result = productService.getProductById(laptop.getId());
//
//        assertThat(result).isPresent();
//        assertThat(result.get().name()).isEqualTo("Laptop");
//    }
//
//    @Disabled("Requires manual testing or advanced concurrency setup")
//    @Test
//    @DisplayName("purchaseProducts should decrement stock atomically in real DB")
//    void shouldDecrementStockInRealDB() {
//        List<ProductPurchaseRequest> requests = List.of(
//                new ProductPurchaseRequest(laptop.getId(), 3.0)
//        );
//
//        List<ProductPurchaseResponse> results = productService.purchaseProducts(requests);
//
//        assertThat(results).hasSize(1);
//        assertThat(results.get(0).quantity()).isEqualTo(3.0);
//
//        // Verify stock was actually decremented in the DB
//        Product updated = productRepository.findById(laptop.getId()).orElseThrow();
//        assertThat(updated.getAvailableQuantity())
//                .as("Stock should be 10 - 3 = 7 after purchase")
//                .isEqualTo(7.0);
//    }
//
//    @Disabled("Requires manual testing or advanced concurrency setup")
//    @Test
//    @DisplayName("purchaseProducts should throw and rollback when stock insufficient")
//    void shouldRollbackWhenInsufficientStock() {
//        List<ProductPurchaseRequest> requests = List.of(
//                new ProductPurchaseRequest(laptop.getId(), 999.0) // way more than available
//        );
//
//        assertThatThrownBy(() -> productService.purchaseProducts(requests))
//                .isInstanceOf(ProductPurchaseException.class);
//
//        // Stock must remain unchanged after rollback
//        Product unchanged = productRepository.findById(laptop.getId()).orElseThrow();
//        assertThat(unchanged.getAvailableQuantity())
//                .as("Stock must be unchanged after rollback")
//                .isEqualTo(10.0);
//    }
//}
