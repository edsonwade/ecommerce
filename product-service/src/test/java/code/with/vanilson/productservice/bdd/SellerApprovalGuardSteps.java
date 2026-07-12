package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.InventoryReservationService;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductMapper;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.ProductService;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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
import static org.mockito.Mockito.when;

/**
 * SellerApprovalGuardSteps — BDD step definitions for the Fase 2 seller approval
 * write-guard (seller_approval_guard.feature).
 * <p>
 * Same POJO+Mockito pattern as {@link TenantIsolationSteps}: the repository is mocked so
 * scenarios run without a database, and the behaviour under test is the guard inside
 * {@link ProductService} — a SELLER principal whose sellerStatus is PENDING_APPROVAL or
 * SUSPENDED must be rejected before any repository interaction.
 *
 * @author vamuhong
 */
public class SellerApprovalGuardSteps {

    private ProductRepository mockRepo;
    private ProductService productService;

    private Product ownedProduct;
    private Throwable writeError;
    private boolean writeSucceeded;

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

        ownedProduct = null;
        writeError = null;
        writeSucceeded = false;
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
        Product p = new Product(id, name, "A product", 5.0, BigDecimal.valueOf(100), category);
        return p;
    }

    // ------------------------------------------------------------------

    @Given("a seller authenticated with seller status {string}")
    public void aSellerAuthenticatedWithStatus(String sellerStatus) {
        init();
        authAs(7L, "SELLER", sellerStatus);
    }

    @Given("a seller authenticated with no seller status claim")
    public void aSellerAuthenticatedWithNoClaim() {
        init();
        authAs(7L, "SELLER", null);
    }

    @Given("an admin is authenticated")
    public void anAdminIsAuthenticated() {
        init();
        authAs(1L, "ADMIN", null);
    }

    @Given("the seller owns product {int} named {string}")
    public void theSellerOwnsProduct(int id, String name) {
        ownedProduct = product(id, name);
        ownedProduct.setCreatedBy("7");
        when(mockRepo.findById(id)).thenReturn(Optional.of(ownedProduct));
    }

    @When("the seller creates a product {string}")
    public void theSellerCreatesAProduct(String name) {
        try {
            productService.createProduct(product(0, name));
            writeSucceeded = true;
        } catch (ProductForbiddenException ex) {
            writeError = ex;
        }
    }

    @When("the seller updates product {int} to {string}")
    public void theSellerUpdatesProduct(int id, String newName) {
        try {
            productService.updateProduct(id, product(id, newName));
            writeSucceeded = true;
        } catch (ProductForbiddenException ex) {
            writeError = ex;
        }
    }

    @Then("the product write is forbidden with reason {string}")
    public void theProductWriteIsForbidden(String messageKey) {
        assertThat(writeSucceeded)
                .as("the write must not have gone through")
                .isFalse();
        assertThat(writeError)
                .isInstanceOf(ProductForbiddenException.class);
        assertThat(((ProductForbiddenException) writeError).getMessageKey())
                .isEqualTo(messageKey);
    }

    @Then("the product write succeeds")
    public void theProductWriteSucceeds() {
        assertThat(writeError)
                .as("no error expected for an allowed write")
                .isNull();
        assertThat(writeSucceeded).isTrue();
    }
}
