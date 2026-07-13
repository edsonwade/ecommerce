package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.category.CategoryRequest;
import code.with.vanilson.productservice.category.CategoryResponse;
import code.with.vanilson.productservice.category.CategoryService;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.tenantcontext.TenantContext;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CategorySteps — BDD step definitions for the Fase 4 category lifecycle
 * (category_management.feature).
 * <p>
 * POJO + Mockito, matching {@link ProductStatusSteps}: a real {@link CategoryService} is
 * wired over mocked repositories so scenarios run without a database. Step phrasing is
 * deliberately prefixed "category feature" — the Cucumber glue package is shared across
 * all step classes, so patterns must not collide with the other suites.
 *
 * @author vamuhong
 */
public class CategorySteps {

    private CategoryRepository mockCategoryRepo;
    private ProductRepository mockProductRepo;
    private CategoryService categoryService;

    private CategoryResponse response;
    private Category savedCategory;
    private ProductConflictException conflictError;
    private ProductNotFoundException notFoundError;

    private void init() {
        mockCategoryRepo = Mockito.mock(CategoryRepository.class);
        mockProductRepo = Mockito.mock(ProductRepository.class);
        MessageSource mockMessageSource = Mockito.mock(MessageSource.class);
        when(mockMessageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mockCategoryRepo.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(500);
            }
            return c;
        });

        categoryService = new CategoryService(mockCategoryRepo, mockProductRepo, mockMessageSource);

        response = null;
        savedCategory = null;
        conflictError = null;
        notFoundError = null;
    }

    @After
    public void clearContexts() {
        TenantContext.clear();
    }

    private Category stored(int id, String name) {
        return Category.builder().id(id).name(name).description(name + " desc").tenantId("default").build();
    }

    // ------------------------------------------------------------------

    @Given("the category feature is ready")
    public void theCategoryFeatureIsReady() {
        init();
    }

    @Given("the category feature already has a category named {string}")
    public void theCategoryFeatureAlreadyHasACategoryNamed(String existingName) {
        when(mockCategoryRepo.existsByNameIgnoreCase(anyString()))
                .thenAnswer(inv -> ((String) inv.getArgument(0)).equalsIgnoreCase(existingName));
    }

    @Given("the category feature has a stored category {int} named {string}")
    public void theCategoryFeatureHasAStoredCategory(int id, String name) {
        when(mockCategoryRepo.findById(id)).thenReturn(Optional.of(stored(id, name)));
    }

    @Given("the category feature category {int} has {int} referencing products")
    public void theCategoryFeatureCategoryHasReferencingProducts(int id, int count) {
        when(mockProductRepo.countByCategoryId(id)).thenReturn((long) count);
    }

    @When("the category feature creates a category named {string} described as {string}")
    public void theCategoryFeatureCreatesACategory(String name, String description) {
        response = categoryService.createCategory(new CategoryRequest(name, description));
    }

    @When("the category feature creates a category named {string} expecting a conflict")
    public void theCategoryFeatureCreatesACategoryExpectingConflict(String name) {
        try {
            response = categoryService.createCategory(new CategoryRequest(name, "dup"));
        } catch (ProductConflictException ex) {
            conflictError = ex;
        }
    }

    @When("the category feature renames category {int} to {string}")
    public void theCategoryFeatureRenamesCategory(int id, String newName) {
        response = categoryService.updateCategory(id, new CategoryRequest(newName, "renamed"));
    }

    @When("the category feature renames category {int} to {string} expecting a failure")
    public void theCategoryFeatureRenamesCategoryExpectingFailure(int id, String newName) {
        try {
            response = categoryService.updateCategory(id, new CategoryRequest(newName, "renamed"));
        } catch (ProductNotFoundException ex) {
            notFoundError = ex;
        }
    }

    @When("the category feature deletes category {int}")
    public void theCategoryFeatureDeletesCategory(int id) {
        categoryService.deleteCategory(id);
    }

    @When("the category feature deletes category {int} expecting a conflict")
    public void theCategoryFeatureDeletesCategoryExpectingConflict(int id) {
        try {
            categoryService.deleteCategory(id);
        } catch (ProductConflictException ex) {
            conflictError = ex;
        }
    }

    // ------------------------------------------------------------------

    @Then("the category feature saved a category named {string}")
    public void theCategoryFeatureSavedACategoryNamed(String expected) {
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(mockCategoryRepo).save(captor.capture());
        savedCategory = captor.getValue();
        assertThat(savedCategory.getName()).isEqualTo(expected);
    }

    @Then("the created category feature response is named {string}")
    public void theCreatedCategoryFeatureResponseIsNamed(String expected) {
        assertThat(response).as("a create response must have been returned").isNotNull();
        assertThat(response.name()).isEqualTo(expected);
    }

    @Then("the category feature saved category has a non-blank tenant")
    public void theCategoryFeatureSavedCategoryHasANonBlankTenant() {
        assertThat(savedCategory)
                .as("the saved category must have been captured first").isNotNull();
        assertThat(savedCategory.getTenantId())
                .as("tenant_id is NOT NULL in the schema — it must always be stamped")
                .isNotBlank();
    }

    @Then("the category feature create is rejected with reason {string}")
    public void theCategoryFeatureCreateIsRejected(String messageKey) {
        assertThat(conflictError)
                .as("the create must have been rejected as a conflict").isNotNull();
        assertThat(conflictError.getMessageKey()).isEqualTo(messageKey);
        verify(mockCategoryRepo, never()).save(any());
    }

    @Then("the category feature update fails as not found")
    public void theCategoryFeatureUpdateFailsAsNotFound() {
        assertThat(response).as("no response is expected for a missing category").isNull();
        assertThat(notFoundError)
                .as("updating a missing category must fail as not found")
                .isInstanceOf(ProductNotFoundException.class);
        verify(mockCategoryRepo, never()).save(any());
    }

    @Then("the category feature deleted category {int}")
    public void theCategoryFeatureDeletedCategory(int id) {
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(mockCategoryRepo).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
    }

    @Then("the category feature delete is rejected with reason {string}")
    public void theCategoryFeatureDeleteIsRejected(String messageKey) {
        assertThat(conflictError)
                .as("the delete must have been rejected as a conflict").isNotNull();
        assertThat(conflictError.getMessageKey()).isEqualTo(messageKey);
        verify(mockCategoryRepo, never()).delete(any());
    }
}
