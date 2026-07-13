package code.with.vanilson.productservice.category;

import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.tenantcontext.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CategoryService — Application Layer (Fase 4).
 * <p>
 * Owns the category lifecycle (list / create / update / delete). Kept separate from
 * {@code ProductService} (Single Responsibility): product concerns and category
 * administration change for different reasons. The existing read
 * {@code ProductService.getCategories()} (backing {@code GET /products/categories})
 * is intentionally left untouched.
 * <p>
 * All error messages resolve from {@code messages.properties} via {@link MessageSource};
 * conflicts surface as {@link ProductConflictException} (409) and missing rows as
 * {@link ProductNotFoundException} (404), both flowing through the global handler.
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MessageSource messageSource;

    public CategoryService(CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           MessageSource messageSource) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.messageSource = messageSource;
    }

    /** Returns all categories (id + name + description). Public read — not tenant-filtered. */
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Creates a category. Rejects a duplicate name (case-insensitive) with 409 before the
     * INSERT so the client gets a clean {@code category.name.exists} rather than a raw
     * constraint 500. Stamps {@code tenant_id} (NOT NULL) from the request context,
     * mirroring product creation.
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ProductConflictException(
                    resolve("category.name.exists", name), "category.name.exists");
        }
        Category category = Category.builder()
                .name(name)
                .description(request.description())
                .tenantId(currentTenant())
                .build();
        Category saved = categoryRepository.save(category);
        log.info(resolve("category.log.created", saved.getId(), saved.getName()));
        return toResponse(saved);
    }

    /**
     * Updates a category's name/description. 404 if it does not exist; 409 if the new name
     * collides with a different existing category (a category may keep its own name).
     */
    @Transactional
    public CategoryResponse updateCategory(Integer id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        resolve("category.not.found", id), "category.not.found"));
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ProductConflictException(
                    resolve("category.name.exists", name), "category.name.exists");
        }
        category.setName(name);
        category.setDescription(request.description());
        Category saved = categoryRepository.save(category);
        log.info(resolve("category.log.updated", saved.getId(), saved.getName()));
        return toResponse(saved);
    }

    /**
     * Deletes a category. 404 if missing; 409 if any product still references it — the
     * entity's {@code cascade = REMOVE} on products would otherwise delete live products,
     * so the reference count is the guard.
     */
    @Transactional
    public void deleteCategory(Integer id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        resolve("category.not.found", id), "category.not.found"));
        long referencing = productRepository.countByCategoryId(id);
        if (referencing > 0) {
            throw new ProductConflictException(
                    resolve("category.delete.has.products", category.getName(), referencing),
                    "category.delete.has.products");
        }
        categoryRepository.delete(category);
        log.info(resolve("category.log.deleted", id));
    }

    // -------------------------------------------------------

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
    }

    private String currentTenant() {
        String tenantId = TenantContext.getCurrentTenantId();
        return tenantId != null && !tenantId.isBlank() ? tenantId : "default";
    }

    private String resolve(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
