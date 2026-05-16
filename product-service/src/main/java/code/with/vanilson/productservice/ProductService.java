package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.category.CategoryResponse;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ProductNullException;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ProductService — Application Layer
 * <p>
 * Core business logic for product management and stock reservation.
 * <p>
 * KEY CHANGES FROM ORIGINAL:
 * 1. All hardcoded exception messages replaced with MessageSource resolution.
 * 2. L2 Redis cache via @Cacheable / @CacheEvict / @CachePut annotations.
 *    Products are cached for 30 minutes (TTL configured in product-service.yml).
 *    Cache is evicted on every write — event-driven invalidation in Phase 3.
 * 3. Pagination: getAllProducts() now accepts Pageable for scalable list queries.
 * 4. purchaseProducts() uses properly resolved messages for all error paths.
 * 5. Single Responsibility (SOLID-S): each method has one reason to change.
 * 6. Dependency Inversion (SOLID-D): depends on repository/messageSource abstractions.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class ProductService {

    private static final String CACHE_PRODUCTS = "products";
    private static final String CACHE_PRODUCT_LIST = "product-list";

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final MessageSource messageSource;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          ProductMapper productMapper,
                          MessageSource messageSource,
                          CategoryRepository categoryRepository) {
        this.productMapper = productMapper;
        this.productRepository = productRepository;
        this.messageSource = messageSource;
        this.categoryRepository = categoryRepository;
    }

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    /**
     * Returns a paginated list of all products.
     * Cached in Redis under 'product-list' (invalidated on any product write).
     *
     * @param pageable pagination and sort parameters
     * @return Page of ProductResponse DTOs
     */
    @Cacheable(value = CACHE_PRODUCT_LIST, key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        Page<ProductResponse> page = productRepository.findAll(pageable)
                .map(productMapper::fromProduct);
        log.info(resolve("product.log.all.found", page.getTotalElements()));
        return page;
    }

    /**
     * Returns a single product by ID.
     * Cached individually in Redis under 'products::{id}'.
     *
     * @param id product ID
     * @return ProductResponse DTO
     */
    @Cacheable(value = CACHE_PRODUCTS, key = "#id")
    public ProductResponse getProductById(int id) {
        return productRepository.findById(id)
                .map(product -> {
                    log.info(resolve("product.log.found.by.id", id, product.getName()));
                    return productMapper.fromProduct(product);
                })
                .orElseThrow(() -> new ProductNotFoundException(
                        resolve("product.not.found", id), "product.not.found"));
    }

    /** Returns all product categories (id + name + description). */
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getDescription()))
                .toList();
    }

    /**
     * Searches and filters products by name/description and/or category, with sorting and pagination.
     *
     * @param query      optional text to match against name or description (case-insensitive)
     * @param categoryId optional category ID to filter by
     * @param sortBy     field to sort by (default: "name")
     * @param sortDir    sort direction: "asc" or "desc" (default: "asc")
     * @param page       zero-based page number
     * @param size       page size
     * @return Page of ProductResponse matching the criteria
     */
    public Page<ProductResponse> searchProducts(String query, Integer categoryId,
                                                String sortBy, String sortDir,
                                                int page, int size) {
        Sort.Direction direction = Sort.Direction.fromString(sortDir);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Specification<Product> spec = Specification.where(null);

        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }
        if (categoryId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }

        return productRepository.findAll(spec, pageable).map(productMapper::fromProduct);
    }

    // -------------------------------------------------------
    // WRITE
    // -------------------------------------------------------

    /**
     * Creates a new product. Evicts product-list cache on success.
     */
    @Transactional
    @CacheEvict(value = CACHE_PRODUCT_LIST, allEntries = true)
    public ProductRequest createProduct(Product product) {
        if (product == null) {
            throw new ProductNullException(
                    resolve("product.null"), "product.null");
        }
        if (product.getCategory() == null) {
            throw new ProductNullException(
                    resolve("product.category.null"), "product.category.null");
        }
        SecurityPrincipal p = currentPrincipal();
        product.setCreatedBy(p != null ? String.valueOf(p.userId()) : "system");
        String tenantId = TenantContext.getCurrentTenantId();
        product.setTenantId(tenantId != null ? tenantId : "default-tenant");
        log.info(resolve("product.log.ownership.stamp", product.getName(), product.getCreatedBy()));
        Product saved = productRepository.save(product);
        log.info(resolve("product.log.created", saved.getId(), saved.getName()));
        return productMapper.toProductRequest(saved);
    }

    /**
     * Updates an existing product by ID.
     * Updates the cache entry for this product and evicts the list cache.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_PRODUCT_LIST, allEntries = true),
        @CacheEvict(value = CACHE_PRODUCTS, key = "#id")
    })
    public ProductRequest updateProduct(int id, Product product) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        resolve("product.not.found", id), "product.not.found"));
        SecurityPrincipal p = currentPrincipal();
        if (p != null && !"ADMIN".equals(p.role())) {
            if (!String.valueOf(p.userId()).equals(existing.getCreatedBy())) {
                throw new ProductForbiddenException(
                        resolve("product.access.denied"), "product.access.denied");
            }
        }
        if (p != null) {
            existing.setUpdatedBy(String.valueOf(p.userId()));
            log.info(resolve("product.log.ownership.update", id, p.userId()));
        }
        applyUpdates(existing, product);
        Product saved = productRepository.save(existing);
        log.info(resolve("product.log.updated", saved.getId(), saved.getName()));
        return productMapper.toProductRequest(saved);
    }

    /**
     * Deletes a product by ID. Evicts all related cache entries.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_PRODUCTS, key = "#id"),
        @CacheEvict(value = CACHE_PRODUCT_LIST, allEntries = true)
    })
    public void deleteProduct(int id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        resolve("product.not.found", id), "product.not.found"));
        SecurityPrincipal p = currentPrincipal();
        if (p != null && !"ADMIN".equals(p.role())) {
            if (!String.valueOf(p.userId()).equals(product.getCreatedBy())) {
                throw new ProductForbiddenException(
                        resolve("product.access.denied"), "product.access.denied");
            }
        }
        productRepository.deleteById(product.getId());
        log.info(resolve("product.log.deleted", id));
    }

    // -------------------------------------------------------
    // PURCHASE (stock reservation)
    // -------------------------------------------------------

    /**
     * Reserves stock for a list of products in a single atomic transaction.
     * Race condition safety: SELECT FOR UPDATE via JPA pessimistic lock
     * (configured in ProductRepository.findAllByIdInOrderById).
     * <p>
     * Throws ProductPurchaseException (HTTP 422) if:
     * - Any productId does not exist
     * - Any product has insufficient stock
     * <p>
     * Cache: evicts all product entries after purchase (stock changed).
     *
     * @param request list of product purchase requests
     * @return list of purchased product details
     */
    @Transactional(rollbackFor = ProductPurchaseException.class)
    @Caching(evict = {
        @CacheEvict(value = CACHE_PRODUCTS, allEntries = true),
        @CacheEvict(value = CACHE_PRODUCT_LIST, allEntries = true)
    })
    public List<ProductPurchaseResponse> purchaseProducts(List<ProductPurchaseRequest> request) {
        if (request == null || request.isEmpty()) {
            throw new ProductPurchaseException(
                    resolve("product.purchase.list.empty"), "product.purchase.list.empty");
        }

        log.info(resolve("product.log.purchase.start", request.size()));

        List<Integer> productIds = request.stream()
                .map(ProductPurchaseRequest::productId)
                .toList();

        List<Product> storedProducts = productRepository.findAllByIdInOrderById(productIds);

        if (productIds.size() != storedProducts.size()) {
            List<Integer> foundIds = storedProducts.stream().map(Product::getId).toList();
            List<Integer> missingIds = productIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new ProductPurchaseException(
                    resolve("product.purchase.not.found", missingIds.toString()),
                    "product.purchase.not.found");
        }

        List<ProductPurchaseRequest> sortedRequest = request.stream()
                .sorted(Comparator.comparing(ProductPurchaseRequest::productId))
                .toList();

        List<ProductPurchaseResponse> purchased = new ArrayList<>();

        for (int i = 0; i < storedProducts.size(); i++) {
            Product product = storedProducts.get(i);
            ProductPurchaseRequest req = sortedRequest.get(i);

            log.info(resolve("product.log.purchase.item",
                    product.getId(), req.quantity(), product.getAvailableQuantity()));

            if (product.getAvailableQuantity() < req.quantity()) {
                throw new ProductPurchaseException(
                        resolve("product.purchase.insufficient.stock",
                                req.productId(), product.getAvailableQuantity(), req.quantity()),
                        "product.purchase.insufficient.stock");
            }

            double newQty = product.getAvailableQuantity() - req.quantity();
            product.setAvailableQuantity(newQty);
            productRepository.save(product);

            log.info(resolve("product.log.stock.updated", product.getId(), newQty));
            purchased.add(productMapper.toproductPurchaseResponse(product, req.quantity()));
        }

        log.info(resolve("product.log.purchase.complete", purchased.size()));
        return purchased;
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    private void applyUpdates(Product existing, Product updated) {
        if (updated.getName() == null) {
            throw new ProductNullException(resolve("product.name.null"), "product.name.null");
        }
        if (updated.getDescription() == null) {
            throw new ProductNullException(resolve("product.description.null"), "product.description.null");
        }
        if (updated.getAvailableQuantity() < 0.0) {
            throw new ProductNullException(resolve("product.quantity.negative"), "product.quantity.negative");
        }
        if (updated.getPrice() == null) {
            throw new ProductNullException(resolve("product.price.null"), "product.price.null");
        }
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setAvailableQuantity(updated.getAvailableQuantity());
        existing.setPrice(updated.getPrice());
        if (updated.getImageUrl() != null) {
            existing.setImageUrl(updated.getImageUrl());
        }
    }

    /** Resolves a message key from messages.properties with optional arguments. */
    private String resolve(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private SecurityPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityPrincipal sp)) return null;
        return sp;
    }
}
