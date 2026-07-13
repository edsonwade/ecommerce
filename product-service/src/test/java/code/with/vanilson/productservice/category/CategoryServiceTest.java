package code.with.vanilson.productservice.category;

import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CategoryServiceTest — unit tests (JUnit 5 + Mockito) for the Fase 4 category lifecycle.
 * <p>
 * Repositories and MessageSource are mocked; the service under test is real. Nested classes
 * mirror the operations (List / Create / Update / Delete). No database — the DB-level
 * uniqueness and referential guards are exercised in {@code CategoryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService — Unit Tests")
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository  productRepository;
    @Mock private MessageSource      messageSource;

    @InjectMocks
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(categoryRepository.save(any(Category.class)))
                .thenAnswer(inv -> {
                    Category c = inv.getArgument(0);
                    if (c.getId() == null) {
                        c.setId(500); // simulate the generated id
                    }
                    return c;
                });
    }

    private Category stored(Integer id, String name) {
        return Category.builder().id(id).name(name).description(name + " desc").tenantId("default").build();
    }

    // -------------------------------------------------------

    @Nested
    @DisplayName("getAllCategories")
    class ListAll {

        @Test
        @DisplayName("maps every entity to a CategoryResponse")
        void mapsEntitiesToResponses() {
            when(categoryRepository.findAll())
                    .thenReturn(List.of(stored(1, "Keyboards"), stored(2, "Mice")));

            List<CategoryResponse> result = categoryService.getAllCategories();

            assertThat(result).extracting(CategoryResponse::name)
                    .containsExactly("Keyboards", "Mice");
            assertThat(result).extracting(CategoryResponse::id)
                    .containsExactly(1, 2);
        }
    }

    @Nested
    @DisplayName("createCategory")
    class Create {

        @Test
        @DisplayName("persists a unique category, trims the name and stamps the tenant")
        void persistsUniqueCategory() {
            when(categoryRepository.existsByNameIgnoreCase("Cables")).thenReturn(false);

            CategoryResponse response = categoryService.createCategory(
                    new CategoryRequest("  Cables  ", "Assorted cables"));

            ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("Cables");
            assertThat(saved.getValue().getDescription()).isEqualTo("Assorted cables");
            // No bound tenant in a unit test → falls back to the single-tenant "default"
            // (mirrors ProductService.createProduct), satisfying the NOT NULL column.
            assertThat(saved.getValue().getTenantId()).isEqualTo("default");
            assertThat(response.name()).isEqualTo("Cables");
        }

        @Test
        @DisplayName("rejects a duplicate name (case-insensitive) with 409 and never saves")
        void rejectsDuplicateName() {
            when(categoryRepository.existsByNameIgnoreCase("keyboards")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.createCategory(
                    new CategoryRequest("keyboards", "dup")))
                    .isInstanceOf(ProductConflictException.class)
                    .satisfies(ex -> assertThat(((ProductConflictException) ex).getMessageKey())
                            .isEqualTo("category.name.exists"));

            verify(categoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateCategory")
    class Update {

        @Test
        @DisplayName("updates name and description when found and no collision")
        void updatesWhenFound() {
            when(categoryRepository.findById(5)).thenReturn(Optional.of(stored(5, "Old")));
            when(categoryRepository.existsByNameIgnoreCaseAndIdNot("New", 5)).thenReturn(false);

            CategoryResponse response = categoryService.updateCategory(5, new CategoryRequest("New", "New desc"));

            ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("New");
            assertThat(saved.getValue().getDescription()).isEqualTo("New desc");
            assertThat(response.name()).isEqualTo("New");
        }

        @Test
        @DisplayName("keeping the category's own name is allowed (no false duplicate)")
        void keepingOwnNameAllowed() {
            when(categoryRepository.findById(5)).thenReturn(Optional.of(stored(5, "Keyboards")));
            when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Keyboards", 5)).thenReturn(false);

            categoryService.updateCategory(5, new CategoryRequest("Keyboards", "renamed desc"));

            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("throws 404 when the category does not exist")
        void throwsWhenMissing() {
            when(categoryRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.updateCategory(99, new CategoryRequest("X", "y")))
                    .isInstanceOf(ProductNotFoundException.class)
                    .satisfies(ex -> assertThat(((ProductNotFoundException) ex).getMessageKey())
                            .isEqualTo("category.not.found"));

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws 409 when the new name collides with a different category")
        void throwsWhenNameCollides() {
            when(categoryRepository.findById(5)).thenReturn(Optional.of(stored(5, "Old")));
            when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Mice", 5)).thenReturn(true);

            assertThatThrownBy(() -> categoryService.updateCategory(5, new CategoryRequest("Mice", "dup")))
                    .isInstanceOf(ProductConflictException.class)
                    .satisfies(ex -> assertThat(((ProductConflictException) ex).getMessageKey())
                            .isEqualTo("category.name.exists"));

            verify(categoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    class Delete {

        @Test
        @DisplayName("deletes when no product references the category")
        void deletesWhenUnreferenced() {
            Category category = stored(5, "Cables");
            when(categoryRepository.findById(5)).thenReturn(Optional.of(category));
            when(productRepository.countByCategoryId(5)).thenReturn(0L);

            categoryService.deleteCategory(5);

            verify(categoryRepository).delete(category);
        }

        @Test
        @DisplayName("throws 404 when the category does not exist")
        void throwsWhenMissing() {
            when(categoryRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.deleteCategory(99))
                    .isInstanceOf(ProductNotFoundException.class)
                    .satisfies(ex -> assertThat(((ProductNotFoundException) ex).getMessageKey())
                            .isEqualTo("category.not.found"));

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws 409 when products still reference the category")
        void throwsWhenReferenced() {
            when(categoryRepository.findById(5)).thenReturn(Optional.of(stored(5, "Keyboards")));
            when(productRepository.countByCategoryId(5)).thenReturn(3L);

            assertThatThrownBy(() -> categoryService.deleteCategory(5))
                    .isInstanceOf(ProductConflictException.class)
                    .satisfies(ex -> assertThat(((ProductConflictException) ex).getMessageKey())
                            .isEqualTo("category.delete.has.products"));

            verify(categoryRepository, never()).delete(any());
        }
    }
}
