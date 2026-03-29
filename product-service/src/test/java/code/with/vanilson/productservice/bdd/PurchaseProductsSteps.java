package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.ProductPurchaseRequest;
import code.with.vanilson.productservice.ProductPurchaseResponse;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PurchaseProductsSteps — BDD Step Definitions
 * <p>
 * Implements the Cucumber steps defined in purchase_products.feature.
 * Uses Spring context injection (@Autowired) for real service + repository.
 * <p>
 * These steps run against a test application context (Testcontainers or H2).
 * The feature file owns the readable specification; this class owns the implementation.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class PurchaseProductsSteps {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;

    private List<ProductPurchaseResponse> purchaseResult;
    private Exception caughtException;

    @Before
    public void resetState() {
        purchaseResult = null;
        caughtException = null;
        productRepository.deleteAll();
    }

    @Given("the product catalog contains the following products:")
    public void theProductCatalogContains(DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps();
        rows.forEach(row -> {
            code.with.vanilson.productservice.Product p =
                    code.with.vanilson.productservice.Product.builder()
                            .name(row.get("name"))
                            .description(row.get("name") + " description")
                            .availableQuantity(Double.parseDouble(row.get("availableQuantity")))
                            .price(new BigDecimal(row.get("price")))
                            .build();
            productRepository.save(p);
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
        productRepository.findById(productId).ifPresent(p ->
                assertThat(p.getAvailableQuantity())
                        .as("Available quantity for product " + productId + " should be " + expectedQty)
                        .isEqualTo((double) expectedQty)
        );
    }

    @And("the available quantity for product {int} should remain {int}")
    public void availableQuantityShouldRemain(int productId, int expectedQty) {
        availableQuantityShouldBe(productId, expectedQty);
    }
}
