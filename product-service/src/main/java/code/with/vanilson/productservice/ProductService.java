package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.CategoryRepository;
import code.with.vanilson.productservice.category.CategoryResponse;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ProductNullException;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
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
import java.util.List;
import java.util.Optional;

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
    private final InventoryReservationService inventoryReservationService;
    private final TenantHibernateFilterActivator filterActivator;

    public ProductService(ProductRepository productRepository,
                          ProductMapper productMapper,
                          MessageSource messageSource,
                          CategoryRepository categoryRepository,
                          InventoryReservationService inventoryReservationService,
                          TenantHibernateFilterActivator filterActivator) {
        this.productMapper = productMapper;
        this.productRepository = productRepository;
        this.messageSource = messageSource;
        this.categoryRepository = categoryRepository;
        this.inventoryReservationService = inventoryReservationService;
        this.filterActivator = filterActivator;
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
    @Cacheable(value = CACHE_PRODUCT_LIST,
            key = "#root.target.cacheTenantKey() + '-' + #root.target.catalogScopeKey() "
                    + "+ '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        filterActivator.activateFilter();
        String sellerScope = currentSellerScope();
        Page<ProductResponse> page = (sellerScope != null
                ? productRepository.findByCreatedBy(sellerScope, pageable)
                : productRepository.findAll(pageable))
                .map(productMapper::fromProduct);
        log.info(resolve("product.log.all.found", page.getTotalElements()));
        return page;
    }

    /**
     * Returns a paginated list of the authenticated seller's own products.
     * <p>
     * Marketplace rule: competing sellers must not see each other's products, so this
     * scopes by {@code created_by = principal.userId} (ownership) — the field stamped at
     * creation in {@link #createProduct(Product)}. This is the read-side counterpart to
     * the write-side ownership checks already enforced in {@link #updateProduct} and
     * {@link #deleteProduct}. NOT cached on the shared {@code product-list} key, which is
     * page-only and would leak one seller's products to another.
     *
     * @param pageable pagination and sort parameters (defaults to newest-first)
     * @return Page of the caller's own ProductResponse DTOs (empty if unauthenticated)
     */
    public Page<ProductResponse> getMyProducts(Pageable pageable) {
        filterActivator.activateFilter();
        SecurityPrincipal principal = currentPrincipal();
        if (principal == null) {
            return Page.empty(pageable);
        }
        String createdBy = String.valueOf(principal.userId());
        Page<ProductResponse> page = productRepository.findByCreatedBy(createdBy, pageable)
                .map(productMapper::fromProduct);
        log.info(resolve("product.log.mine.found", page.getTotalElements(), createdBy));
        return page;
    }

    /**
     * Returns a single product by ID.
     * Cached individually in Redis under 'products::{tenant}:{id}'.
     * <p>
     * Tenant safety: a by-id lookup uses {@code repository.findById} (= {@code em.find}),
     * which Hibernate {@code @Filter} does <strong>not</strong> apply to by design — so
     * activating the filter is not enough here. When a tenant is bound to the request we
     * query {@link ProductRepository#findByIdAndTenantId} instead, so a caller of tenant A
     * reading tenant B's product gets a 404, not a cross-tenant leak. Without a tenant
     * (single-tenant dev / anonymous) we fall back to the plain lookup. The cache key is
     * tenant-scoped for the same reason — otherwise tenant A could be served tenant B's
     * cached entry under the shared {@code id} key.
     *
     * @param id product ID
     * @return ProductResponse DTO
     */
    @Cacheable(value = CACHE_PRODUCTS, key = "#root.target.cacheTenantKey() + ':' + #id")
    public ProductResponse getProductById(int id) {
        Optional<Product> found = TenantContext.isPresent()
                ? productRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenantId())
                : productRepository.findById(id);
        return found
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
        filterActivator.activateFilter();
        Sort.Direction direction = Sort.Direction.fromString(sortDir);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Specification<Product> spec = Specification.where(null);

        // Marketplace isolation: a SELLER may only browse/search their own products.
        // Customers, guests and ADMIN search the full cross-seller catalogue.
        String sellerScope = currentSellerScope();
        if (sellerScope != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("createdBy"), sellerScope));
        }

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
        // Stamp the tenant from the request context. The previous "default-tenant" fallback
        // wrote an orphan tag that matched neither the JWT tenant claim nor the seeded data
        // (both "default"); once the read path filters by tenant that orphan would vanish
        // from every query. Fall back to the single-tenant "default" so a product created
        // without a bound tenant is still reachable, consistent with auth and the catalogue.
        String tenantId = TenantContext.getCurrentTenantId();
        product.setTenantId(tenantId != null && !tenantId.isBlank() ? tenantId : "default");
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

        // Reservation core (fetch-lock / validate / deduct) is shared with the
        // Kafka saga path — see InventoryReservationService.
        List<InventoryReservationService.ReservedLine> reserved =
                inventoryReservationService.reserveStock(request.stream()
                        .map(r -> new InventoryReservationService.ReservationItem(r.productId(), r.quantity()))
                        .toList());

        List<ProductPurchaseResponse> purchased = reserved.stream()
                .map(line -> productMapper.toproductPurchaseResponse(line.product(), line.quantity()))
                .toList();

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

    /**
     * Catalogue scope for the current caller.
     * <p>
     * Marketplace rule: a SELLER must only ever see their own products — competing sellers
     * never see each other's catalogue, and a seller can never reach another seller's product
     * to view or tamper with its price. This is the read-side counterpart to the write-side
     * ownership guards in {@link #updateProduct} / {@link #deleteProduct}. Customers, guests
     * and ADMIN get the full cross-seller catalogue.
     *
     * @return the seller's userId (as stored in {@code created_by}) when the caller is a
     *         SELLER, otherwise {@code null} (no scoping → full catalogue)
     */
    private String currentSellerScope() {
        SecurityPrincipal p = currentPrincipal();
        return (p != null && p.isSeller()) ? String.valueOf(p.userId()) : null;
    }

    /**
     * Cache discriminator for {@link #getAllProducts}: keeps a seller's scoped catalogue page
     * from colliding with the public (cross-seller) one under the shared {@code product-list}
     * key. Public so it is reachable from the {@code @Cacheable} SpEL key expression.
     *
     * @return {@code "seller:<id>"} for a SELLER caller, otherwise {@code "all"}
     */
    public String catalogScopeKey() {
        String scope = currentSellerScope();
        return scope == null ? "all" : "seller:" + scope;
    }

    /**
     * Tenant discriminator for cache keys: keeps one tenant's cached catalogue page or
     * product entry from being served to another under a shared, tenant-blind key. Public
     * so it is reachable from the {@code @Cacheable} SpEL key expression.
     *
     * @return the current tenant id, or {@code "none"} when no tenant is bound (single-tenant dev)
     */
    public String cacheTenantKey() {
        String tenant = TenantContext.getCurrentTenantId();
        return (tenant == null || tenant.isBlank()) ? "none" : tenant;
    }
}
