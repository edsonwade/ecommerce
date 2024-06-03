package code.with.vanilson.productservice;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(path = "/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Retrieves all products.
     *
     * @return A ResponseEntity containing a list of ProductResponse if available, or an empty list if no products are found.
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok()
                .body(productService.getAllProducts());
    }

    /**
     * Retrieves a product by its ID.
     *
     * @param id The ID of the product to retrieve.
     * @return A ResponseEntity containing the ProductResponse if found, or a 404 Not Found response if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable int id) {
        Optional<ProductResponse> productResponseOptional = productService.getProductById(id);

        return productResponseOptional
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new product.
     *
     * @param product The product to create.
     * @return A ResponseEntity containing the created ProductRequest.
     */
    @PostMapping("/create")
    public ResponseEntity<ProductRequest> createProduct(@RequestBody @Valid Product product) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(product));
    }

    /**
     * Endpoint for purchasing products based on the provided list of purchase requests.
     *
     * @param request The list of product purchase requests containing product IDs and quantities.
     * @return A ResponseEntity containing a list of product purchase responses indicating the success of each purchase.
     */
    @PostMapping("/purchase")
    public ResponseEntity<List<ProductPurchaseResponse>> purchaseProducts(
            @RequestBody List<ProductPurchaseRequest> request
    ) {
        return ResponseEntity.ok(productService.purchaseProducts(request));
    }

    /**
     * Endpoint to create multiple products in the system.
     *
     * @param products The list of products to be created.
     * @return A ResponseEntity containing the list of created product requests.
     */
    @PostMapping("/create-products")
    public ResponseEntity<List<ProductRequest>> createProducts(@RequestBody @Valid List<Product> products) {
        List<ProductRequest> createdProducts = productService.createProducts(products);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProducts);
    }

    /**
     * Updates an existing product.
     *
     * @param id      The ID of the product to update.
     * @param product The updated product details.
     * @return A ResponseEntity containing the updated ProductRequest.
     */
    @PutMapping("/update/{id}")
    public ResponseEntity<ProductRequest> updateProduct(@PathVariable int id, @RequestBody Product product) {
        var updatedProduct = productService.updateProduct(id, product);
        return ResponseEntity.ok(updatedProduct);
    }

    /**
     * Deletes a product by its ID.
     *
     * @param id The ID of the product to delete.
     * @return A ResponseEntity indicating success if the product is deleted, or an error response if the product is not found.
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable int id) {
        productService.deleteProduct(id);
        return ResponseEntity.accepted().build();
    }

}
