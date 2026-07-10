package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.InventoryReservationService;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductMapper;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductResponse;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * TenantIsolationSteps — BDD step definitions for tenant-scoped product reads (B3 Fase 1b).
 * <p>
 * Uses Mockito for the repository so the scenarios run without a database. The repository is
 * stubbed so {@code findByIdAndTenantId} only returns the product for its owning tenant —
 * mirroring the real query's WHERE {@code tenant_id = :tenantId}. The behaviour under test is
 * {@link ProductService#getProductById}: it must query by the bound tenant, so a caller of one
 * tenant reading another tenant's id gets a not-found, never a leak.
 *
 * @author vamuhong
 */
public class TenantIsolationSteps {

    private ProductRepository mockRepo;
    private ProductService productService;

    private ProductResponse readResult;
    private Throwable readError;

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

        readResult = null;
        readError = null;
    }

    @After
    public void clearTenant() {
        TenantContext.clear();
    }

    @Given("product {int} belongs to tenant {string}")
    public void productBelongsToTenant(int id, String owningTenant) {
        init();
        Category category = new Category(1, "Electronics", "Electronic items");
        Product product = new Product(id, "Widget", "A widget", 5.0, BigDecimal.valueOf(100), category);
        product.setTenantId(owningTenant);
        product.setCreatedBy("9001");

        // The tenant-scoped query only returns the product to its owning tenant, exactly as the
        // real WHERE tenant_id = :tenantId would; every other tenant sees an empty result.
        when(mockRepo.findByIdAndTenantId(eq(id), anyString())).thenAnswer(inv ->
                owningTenant.equals(inv.getArgument(1)) ? Optional.of(product) : Optional.empty());
        // A different (non-existent) id is never present for any tenant.
        when(mockRepo.findByIdAndTenantId(eq(id + 899), anyString())).thenReturn(Optional.empty());
    }

    @When("tenant {string} requests product {int}")
    public void tenantRequestsProduct(String tenant, int id) {
        TenantContext.setCurrentTenantId(tenant);
        try {
            readResult = productService.getProductById(id);
        } catch (ProductNotFoundException ex) {
            readError = ex;
        }
    }

    @Then("the product read succeeds")
    public void theProductReadSucceeds() {
        assertThat(readError).as("no error expected for a same-tenant read").isNull();
        assertThat(readResult).isNotNull();
        assertThat(readResult.name()).isEqualTo("Widget");
    }

    @Then("the product read is not found")
    public void theProductReadIsNotFound() {
        assertThat(readResult).as("no product should be returned across tenants").isNull();
        assertThat(readError)
                .as("a cross-tenant or unknown-id read must be a not-found, not a leak")
                .isInstanceOf(ProductNotFoundException.class);
    }
}
