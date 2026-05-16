package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductMapper;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductResponse;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SearchProductsSteps — BDD step definitions for catalog search feature.
 * <p>
 * Uses Mockito for repository + real ProductMapper so tests run
 * without a database, making them fast and reliable in any CI environment.
 * </p>
 *
 * @author vamuhong
 */
public class SearchProductsSteps {

    private ProductRepository mockRepo;
    private MessageSource mockMessageSource;
    private CategoryRepository mockCategoryRepo;
    private ProductService productService;

    private List<Product> catalogProducts;
    private Page<ProductResponse> searchResult;

    private final Category cat1 = new Category(1, "Electronics", "Electronic items");
    private final Category cat2 = new Category(2, "Accessories", "Accessory items");

    private void init() {
        mockRepo = Mockito.mock(ProductRepository.class);
        mockMessageSource = Mockito.mock(MessageSource.class);
        mockCategoryRepo = Mockito.mock(CategoryRepository.class);

        when(mockMessageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Use real ProductMapper — fromProduct() is package-private and is invoked
        // internally by ProductService (same package), so the real mapper works fine.
        ProductMapper realMapper = new ProductMapper(mockMessageSource);
        productService = new ProductService(mockRepo, realMapper, mockMessageSource, mockCategoryRepo);
        searchResult = null;
        catalogProducts = null;
    }

    @Given("the catalog has products named {string} and {string}")
    public void theCatalogHasProductsNamed(String name1, String name2) {
        init();
        Product p1 = new Product(1, name1, name1 + " description", 5.0, BigDecimal.valueOf(100), cat1);
        Product p2 = new Product(2, name2, name2 + " description", 10.0, BigDecimal.valueOf(200), cat2);
        p1.setCreatedBy("system");
        p2.setCreatedBy("system");
        catalogProducts = List.of(p1, p2);
    }

    @Given("the catalog has a product {string} in category {int} and {string} in category {int}")
    public void theCatalogHasProductsInCategories(String name1, int catId1, String name2, int catId2) {
        init();
        Category c1 = catId1 == 1 ? cat1 : cat2;
        Category c2 = catId2 == 1 ? cat1 : cat2;
        Product p1 = new Product(1, name1, name1 + " description", 5.0, BigDecimal.valueOf(100), c1);
        Product p2 = new Product(2, name2, name2 + " description", 10.0, BigDecimal.valueOf(200), c2);
        p1.setCreatedBy("system");
        p2.setCreatedBy("system");
        catalogProducts = List.of(p1, p2);
    }

    @When("I search for {string}")
    public void iSearchFor(String query) {
        List<Product> matches = catalogProducts.stream()
                .filter(p -> p.getName().toLowerCase().contains(query.toLowerCase())
                        || p.getDescription().toLowerCase().contains(query.toLowerCase()))
                .toList();
        Page<Product> page = new PageImpl<>(matches);
        when(mockRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        searchResult = productService.searchProducts(query, null, "name", "asc", 0, 20);
    }

    @When("I search with no filters")
    public void iSearchWithNoFilters() {
        Page<Product> page = new PageImpl<>(catalogProducts);
        when(mockRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        searchResult = productService.searchProducts(null, null, "name", "asc", 0, 20);
    }

    @When("I filter by category {int}")
    public void iFilterByCategory(int categoryId) {
        List<Product> matches = catalogProducts.stream()
                .filter(p -> p.getCategory() != null && p.getCategory().getId() == categoryId)
                .toList();
        Page<Product> page = new PageImpl<>(matches);
        when(mockRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        searchResult = productService.searchProducts(null, categoryId, "name", "asc", 0, 20);
    }

    @When("I search for {string} and filter by category {int}")
    public void iSearchAndFilterByCategory(String query, int categoryId) {
        List<Product> matches = catalogProducts.stream()
                .filter(p -> (p.getName().toLowerCase().contains(query.toLowerCase())
                        || p.getDescription().toLowerCase().contains(query.toLowerCase()))
                        && p.getCategory() != null && p.getCategory().getId() == categoryId)
                .toList();
        Page<Product> page = new PageImpl<>(matches);
        when(mockRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        searchResult = productService.searchProducts(query, categoryId, "name", "asc", 0, 20);
    }

    @Then("the search results should contain {int} product(s)")
    public void theSearchResultsShouldContain(int count) {
        assertThat(searchResult).isNotNull();
        assertThat(searchResult.getTotalElements()).isEqualTo(count);
    }

    @Then("the search results should be empty")
    public void theSearchResultsShouldBeEmpty() {
        assertThat(searchResult).isNotNull();
        assertThat(searchResult.getContent()).isEmpty();
    }

    @And("the result should include {string}")
    public void theResultShouldInclude(String productName) {
        assertThat(searchResult.getContent())
                .extracting(ProductResponse::name)
                .contains(productName);
    }
}
