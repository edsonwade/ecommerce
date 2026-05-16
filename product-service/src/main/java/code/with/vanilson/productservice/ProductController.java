package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ProductController — Presentation Layer
 * <p>
 * REST controller for product catalog management and stock reservation.
 * Single Responsibility (SOLID-S): HTTP concerns only — no business logic.
 * All business logic is delegated to ProductService.
 * <p>
 * Changes from original:
 * - GET /api/v1/products now supports pagination via Pageable (Page<ProductResponse>)
 * - DELETE returns 204 No Content (was 202 Accepted — semantically wrong)
 * - Full Swagger / OpenAPI annotations on all endpoints
 * - Proper REST paths: /purchase instead of /create, /create instead of explicit path
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product API", description = "Product catalog management and stock reservation")
@SuppressWarnings("all")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    @Operation(summary = "List all products (paginated)",
               description = "Returns a paginated list of all products. Use ?page=0&size=20&sort=name,asc")
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
            @Parameter(hidden = true) Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @Operation(summary = "Get product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable int id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @Operation(summary = "Create a new product")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Product created"),
        @ApiResponse(responseCode = "400", description = "Invalid product data")
    })
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ProductRequest> createProduct(@RequestBody @Valid ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(productMapper.toProduct(request)));
    }

    @Operation(summary = "Create multiple products in batch")
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ProductRequest> createProducts(@RequestBody @Valid ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(productMapper.toProduct(request)));
    }

    @Operation(summary = "Update a product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product updated"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PutMapping("/update/{id}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ProductRequest> updateProduct(
            @PathVariable int id,
            @RequestBody @Valid ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, productMapper.toProduct(request)));
    }

    @Operation(summary = "Delete a product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Product deleted"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable int id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search and filter products",
               description = "Search by name/description and filter by category, with sort and pagination support")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.searchProducts(query, categoryId, sortBy, sortDir, page, size));
    }

    @Operation(summary = "List all categories")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(productService.getCategories());
    }

    @Operation(summary = "Purchase (reserve stock for) a list of products",
               description = "Atomically reserves stock for all products. Fails with 422 if any product has insufficient stock.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock reserved successfully"),
        @ApiResponse(responseCode = "422", description = "Insufficient stock or product not found")
    })
    @PostMapping("/purchase")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductPurchaseResponse>> purchaseProducts(
            @RequestBody @Valid List<ProductPurchaseRequest> request) {
        return ResponseEntity.ok(productService.purchaseProducts(request));
    }
}
