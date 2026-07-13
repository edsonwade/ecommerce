package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.InventoryReservationService;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductMapper;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductResponse;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.ProductStatus;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductStatusSteps — BDD step definitions for the Fase 3 product lifecycle status
 * (product_status.feature).
 * <p>
 * Same POJO+Mockito pattern as {@link SellerApprovalGuardSteps}: the repository is mocked
 * so scenarios run without a database. Step phrasing is deliberately unique ("status
 * feature") — the Cucumber glue package is shared across all step classes, and duplicate
 * step patterns would collide with {@link SellerApprovalGuardSteps}.
 *
 * @author vamuhong
 */
public class ProductStatusSteps {

    private ProductRepository mockRepo;
    private ProductService productService;

    private ProductResponse detailResponse;
    private ProductNotFoundException detailError;
    private Product purchasableProduct;
    private code.with.vanilson.productservice.exception.ProductPurchaseException purchaseError;
    private boolean purchaseSucceeded;
    private ProductResponse statusChangeResponse;

    private void init() {
        mockRepo = Mockito.mock(ProductRepository.class);
        MessageSource mockMessageSource = Mockito.mock(MessageSource.class);
        CategoryRepository mockCategoryRepo = Mockito.mock(CategoryRepository.class);
        TenantHibernateFilterActivator mockFilterActivator =
                Mockito.mock(TenantHibernateFilterActivator.class);

        when(mockMessageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProductMapper realMapper = new ProductMapper(mockMessageSource);
        productService = new ProductService(mockRepo, realMapper, mockMessageSource, mockCategoryRepo,
                new InventoryReservationService(mockRepo, mockMessageSource), mockFilterActivator);

        when(mockRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        detailResponse = null;
        detailError = null;
        purchasableProduct = null;
        purchaseError = null;
        purchaseSucceeded = false;
        statusChangeResponse = null;
    }

    @After
    public void clearContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authAs(long userId, String role, String sellerStatus) {
        var principal = new SecurityPrincipal("bdd@x.com", userId, "default", role, sellerStatus);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Product product(int id, String name) {
        Category category = new Category(1, "Electronics", "Electronic items");
        return new Product(id, name, "A product", 5.0, BigDecimal.valueOf(100), category);
    }

    // ------------------------------------------------------------------

    @Given("an approved seller is signed in for the status feature")
    public void anApprovedSellerIsSignedInForTheStatusFeature() {
        init();
        authAs(7L, "SELLER", "APPROVED");
    }

    @Given("an admin is signed in for the status feature")
    public void anAdminIsSignedInForTheStatusFeature() {
        init();
        authAs(1L, "ADMIN", null);
    }

    @Given("the status feature has a stored product {int} named {string} with status {string} owned by the seller")
    public void theStatusFeatureHasAStoredProduct(int id, String name, String status) {
        Product stored = product(id, name);
        stored.setCreatedBy("7");
        stored.setStatus(ProductStatus.valueOf(status));
        when(mockRepo.findById(id)).thenReturn(Optional.of(stored));
    }

    @Given("the status feature has a stored product {int} named {string} with status {string} owned by another seller")
    public void theStatusFeatureHasAStoredProductOwnedByAnother(int id, String name, String status) {
        Product stored = product(id, name);
        stored.setCreatedBy("999");
        stored.setStatus(ProductStatus.valueOf(status));
        when(mockRepo.findById(id)).thenReturn(Optional.of(stored));
    }

    @When("the seller submits a status-feature product named {string}")
    public void theSellerSubmitsAStatusFeatureProduct(String name) {
        productService.createProduct(product(0, name));
    }

    @When("the status feature requests the detail of product {int}")
    public void theStatusFeatureRequestsTheDetail(int id) {
        detailResponse = productService.getProductById(id);
    }

    @When("the status feature requests the detail of product {int} expecting a failure")
    public void theStatusFeatureRequestsTheDetailExpectingFailure(int id) {
        try {
            detailResponse = productService.getProductById(id);
        } catch (ProductNotFoundException ex) {
            detailError = ex;
        }
    }

    @Then("the product saved by the status feature has status {string}")
    public void theProductSavedHasStatus(String expected) {
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(mockRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus())
                .as("a newly created product must be born with the default status")
                .isEqualTo(ProductStatus.valueOf(expected));
    }

    @Then("the status feature detail response has status {string}")
    public void theDetailResponseHasStatus(String expected) {
        assertThat(detailResponse).as("detail response must have been fetched").isNotNull();
        assertThat(detailResponse.status()).isEqualTo(ProductStatus.valueOf(expected));
    }

    @Then("the status feature detail request fails as not found")
    public void theDetailRequestFailsAsNotFound() {
        assertThat(detailResponse)
                .as("a hidden suspended product must not be returned")
                .isNull();
        assertThat(detailError)
                .as("the read must fail exactly like a nonexistent product (no existence leak)")
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Task 3.3 — purchase-path rejection
    // ------------------------------------------------------------------

    @Given("the status feature has a purchasable product {int} named {string} with status {string} and stock {double}")
    public void theStatusFeatureHasAPurchasableProduct(int id, String name, String status, double stock) {
        Product stored = product(id, name);
        stored.setAvailableQuantity(stock);
        stored.setStatus(ProductStatus.valueOf(status));
        when(mockRepo.findAllByIdInOrderById(List.of(id))).thenReturn(List.of(stored));
        purchasableProduct = stored;
    }

    @When("the status feature purchases {double} units of product {int}")
    public void theStatusFeaturePurchases(double quantity, int id) {
        try {
            productService.purchaseProducts(List.of(
                    new code.with.vanilson.productservice.ProductPurchaseRequest(id, quantity)));
            purchaseSucceeded = true;
        } catch (code.with.vanilson.productservice.exception.ProductPurchaseException ex) {
            purchaseError = ex;
        }
    }

    @Then("the status feature purchase is rejected with reason {string}")
    public void thePurchaseIsRejectedWithReason(String messageKey) {
        assertThat(purchaseSucceeded)
                .as("the purchase must not have gone through")
                .isFalse();
        assertThat(purchaseError)
                .isInstanceOf(code.with.vanilson.productservice.exception.ProductPurchaseException.class);
        assertThat(purchaseError.getMessageKey()).isEqualTo(messageKey);
    }

    @Then("the status feature deducted no stock")
    public void theStatusFeatureDeductedNoStock() {
        Mockito.verify(mockRepo, Mockito.never()).save(any(Product.class));
        assertThat(purchasableProduct.getAvailableQuantity())
                .as("available quantity must be untouched after a rejected purchase")
                .isEqualTo(10.0);
    }

    @Then("the status feature purchase succeeds")
    public void theStatusFeaturePurchaseSucceeds() {
        assertThat(purchaseError)
                .as("no error expected for an ACTIVE product with stock")
                .isNull();
        assertThat(purchaseSucceeded).isTrue();
    }

    // ------------------------------------------------------------------
    // Task 3.4 — admin status management
    // ------------------------------------------------------------------

    @When("the admin sets the status of product {int} to {string} in the status feature")
    public void theAdminSetsTheStatus(int id, String status) {
        statusChangeResponse = productService.updateProductStatus(id, ProductStatus.valueOf(status));
    }

    @Then("the status feature saved status change is {string}")
    public void theSavedStatusChangeIs(String expected) {
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(mockRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ProductStatus.valueOf(expected));
        assertThat(statusChangeResponse)
                .as("the status change must return the updated product")
                .isNotNull();
        assertThat(statusChangeResponse.status()).isEqualTo(ProductStatus.valueOf(expected));
    }
}
