package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.InventoryReservationService;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductMapper;
import code.with.vanilson.productservice.ProductPurchaseRequest;
import code.with.vanilson.productservice.ProductPurchaseResponse;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PurchaseProductsSteps — BDD Step Definitions
 * <p>
 * Implements the Cucumber steps defined in purchase_products.feature.
 * <p>
 * POJO + Mockito, like the sibling step classes in this glue package (this suite
 * runs with DefaultObjectFactory, so there is no Spring context here). The
 * repository is a map-backed Mockito stub; the real ProductService and
 * InventoryReservationService run the actual fetch/validate/deduct logic, so the
 * scenarios exercise the same reservation core as production — just without a
 * database.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
public class PurchaseProductsSteps {

    /** In-memory catalog backing the repository stub. */
    private final Map<Integer, Product> store = new LinkedHashMap<>();

    private ProductService productService;

    private List<ProductPurchaseResponse> purchaseResult;
    private Exception caughtException;

    @Before
    public void resetState() {
        // Hooks run for every scenario in the suite (Cucumber hooks are global to
        // the glue path), so this must stay dependency-free and side-effect safe.
        purchaseResult = null;
        caughtException = null;
        store.clear();
    }

    private void init() {
        ProductRepository mockRepo = Mockito.mock(ProductRepository.class);
        MessageSource mockMessageSource = Mockito.mock(MessageSource.class);
        CategoryRepository mockCategoryRepo = Mockito.mock(CategoryRepository.class);

        when(mockMessageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Map-backed repository: reserveStock() fetches with findAllByIdInOrderById
        // and persists deductions with save() — both routed to the store.
        when(mockRepo.findAllByIdInOrderById(any())).thenAnswer(inv -> {
            List<Integer> ids = inv.getArgument(0);
            return ids.stream()
                    .distinct()
                    .map(store::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Product::getId))
                    .toList();
        });
        when(mockRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            store.put(p.getId(), p);
            return p;
        });

        ProductMapper realMapper = new ProductMapper(mockMessageSource);
        var mockFilterActivator =
                Mockito.mock(code.with.vanilson.tenantcontext.TenantHibernateFilterActivator.class);
        productService = new ProductService(mockRepo, realMapper, mockMessageSource, mockCategoryRepo,
                new InventoryReservationService(mockRepo, mockMessageSource), mockFilterActivator);
    }

    @Given("the product catalog contains the following products:")
    public void theProductCatalogContains(DataTable dataTable) {
        init();
        List<Map<String, String>> rows = dataTable.asMaps();
        rows.forEach(row -> {
            Product p = Product.builder()
                    .id(Integer.parseInt(row.get("id")))
                    .name(row.get("name"))
                    .description(row.get("name") + " description")
                    .availableQuantity(Double.parseDouble(row.get("availableQuantity")))
                    .price(new BigDecimal(row.get("price")))
                    .build();
            store.put(p.getId(), p);
        });
    }

    @When("I request to purchase the following products:")
    public void iRequestToPurchase(DataTable dataTable) {
        List<ProductPurchaseRequest> requests = new ArrayList<>();
        dataTable.asMaps().forEach(row ->
                requests.add(new ProductPurchaseRequest(
                        Integer.parseInt(row.get("productId")),
                        Double.parseDouble(row.get("quantity"))
                ))
        );
        try {
            purchaseResult = productService.purchaseProducts(requests);
        } catch (ProductPurchaseException | ProductNotFoundException e) {
            caughtException = e;
        }
    }

    @When("I request to purchase an empty list of products")
    public void iRequestEmptyPurchase() {
        try {
            purchaseResult = productService.purchaseProducts(List.of());
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("the purchase should succeed")
    public void thePurchaseShouldSucceed() {
        assertThat(caughtException)
                .as("No exception should have been thrown for a successful purchase")
                .isNull();
        assertThat(purchaseResult)
                .as("Purchase result should not be null or empty")
                .isNotNull()
                .isNotEmpty();
    }

    @Then("the purchase should fail with a stock error")
    public void thePurchaseShouldFailWithStockError() {
        assertThat(caughtException)
                .as("A ProductPurchaseException should have been thrown")
                .isInstanceOf(ProductPurchaseException.class);
    }

    @Then("the purchase should fail with a not found error")
    public void thePurchaseShouldFailWithNotFoundError() {
        assertThat(caughtException)
                .as("A ProductPurchaseException (not found) should have been thrown")
                .isInstanceOf(ProductPurchaseException.class);
    }

    @Then("the purchase should fail with a validation error")
    public void thePurchaseShouldFailWithValidationError() {
        assertThat(caughtException)
                .as("A ProductPurchaseException (validation) should have been thrown")
                .isInstanceOf(ProductPurchaseException.class);
    }

    @And("the available quantity for product {int} should be {int}")
    public void availableQuantityShouldBe(int productId, int expectedQty) {
        Product product = store.get(productId);
        assertThat(product)
                .as("Product " + productId + " should exist in the catalog")
                .isNotNull();
        assertThat(product.getAvailableQuantity())
                .as("Available quantity for product " + productId + " should be " + expectedQty)
                .isEqualTo(expectedQty);
    }

    @And("the available quantity for product {int} should remain {int}")
    public void availableQuantityShouldRemain(int productId, int expectedQty) {
        availableQuantityShouldBe(productId, expectedQty);
    }
}
