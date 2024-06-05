package code.with.vanilson.productservice;

import code.with.vanilson.productservice.except.ProductNotFoundException;
import code.with.vanilson.productservice.except.ProductNullException;
import code.with.vanilson.productservice.except.ProductPurchaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ProductService {

    public static final String PRODUCT_WITH_ID_0_WAS_NOT_FOUND = "Product  with id {0} was not found";
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productMapper = productMapper;
        this.productRepository = productRepository;
    }

    /**
     * Retrieves all products and maps them to a list of ProductResponse objects.
     *
     * @return List of ProductResponse objects representing all products
     */
    public List<ProductResponse> getAllProducts() {
        var products = productRepository.findAll();
        log.info("getAllProducts returned {}", products);
        return productMapper.toProductResponse(products);

    }

    /**
     * Retrieves a product by its ID.
     *
     * @param id The ID of the product to retrieve.
     * @return An {@code Optional} containing the {@code ProductResponse} if found, or an empty {@code Optional} if not found.
     * @throws ProductNotFoundException If no product with the specified ID is found.
     */
    public Optional<ProductResponse> getProductById(int id) {
        var products = productRepository.findById(id)
                .map(productMapper::fromProduct)
                .orElseThrow(() -> new ProductNotFoundException(
                        MessageFormat.format(PRODUCT_WITH_ID_0_WAS_NOT_FOUND, id)));

        return Optional.ofNullable(products);

    }

    /**
     * Creates a new product in the system.
     *
     * @param product The product to be created.
     * @return The created product request.
     */
    public ProductRequest createProduct(Product product) {
        if (product == null) {
            throw new ProductNullException("Product must not be null");
        }
        if (product.getCategory() == null) {
            throw new ProductNullException("Product category must not be null");
        }
        var savedProduct = productRepository.save(product);
        log.info("createProduct with saved product {}", savedProduct);
        return productMapper.toProductRequest(savedProduct);
    }

    /**
     * Creates new products in the system.
     *
     * @param products The list of products to be created.
     * @return The list of created product requests.
     */
    public List<ProductRequest> createProducts(List<Product> products) {
        if (products == null || products.isEmpty()) {
            throw new ProductNullException("Product list must not be null or empty");
        }
        List<Product> savedProducts = productRepository.saveAll(products);
        log.info("createProducts with saved products {}", savedProducts);
        return productMapper.toProductRequests(savedProducts);
    }

    /**
     * Updates a product with the given ID using the provided product details.
     *
     * @param id      The ID of the product to update.
     * @param product The updated product details.
     * @return The updated product as a {@code ProductRequest}.
     * @throws ProductNotFoundException If no product with the specified ID is found.
     */
    public ProductRequest updateProduct(int id, Product product) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(MessageFormat.format(PRODUCT_WITH_ID_0_WAS_NOT_FOUND
                        , id)));

        // Update existing product with new values
        updateProductDetails(existingProduct, product);

        // Save and return the updated product
        var savedProduct = productRepository.save(existingProduct);
        return productMapper.toProductRequest(savedProduct);
    }

    /**
     * Deletes a product with the given ID.
     *
     * @param id The ID of the product to delete.
     * @throws ProductNotFoundException If no product with the specified ID is found.
     */
    public void deleteProduct(int id) {
        var products = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        MessageFormat.format(PRODUCT_WITH_ID_0_WAS_NOT_FOUND, id)));

        productRepository.deleteById(products.getId());
    }

    /**
     * Updates the details of an existing product with the provided updated product details.
     * Checks for null values in the updated product details and ensures that certain fields are not null or negative.
     *
     * @param existingProduct The existing product whose details are being updated.
     * @param updatedProduct  The updated product details to apply.
     * @throws ProductNullException If any of the updated product details are null or if the available quantity is negative.
     */
    private void updateProductDetails(Product existingProduct, Product updatedProduct) {
        if (updatedProduct.getName() != null) {
            existingProduct.setName(updatedProduct.getName());
        } else {
            throw new ProductNullException("Product name must not be null");
        }
        if (updatedProduct.getDescription() != null) {
            existingProduct.setDescription(updatedProduct.getDescription());
        } else {
            throw new ProductNullException("Product description must not be null");
        }
        if (updatedProduct.getAvailableQuantity() >= 0.0) {
            existingProduct.setAvailableQuantity(updatedProduct.getAvailableQuantity());
        } else {
            throw new ProductNullException("Product available quantity must be greater than 0");
        }
        if (updatedProduct.getPrice() != null) {
            existingProduct.setPrice(updatedProduct.getPrice());
        } else {
            throw new ProductNullException("Product price must not be null");
        }
    }

    /**
     * Processes the purchase of products based on the provided list of purchase requests.
     *
     * @param request The list of product purchase requests containing product IDs and quantities.
     * @return A list of product purchase responses indicating the success of each purchase.
     * @throws ProductPurchaseException If there are any issues with purchasing the products, such as insufficient stock or non-existing products.
     */
    @Transactional(rollbackFor = ProductPurchaseException.class)
    public List<ProductPurchaseResponse> purchaseProducts(
            List<ProductPurchaseRequest> request
    ) {
        var productIds = request
                .stream()
                .map(ProductPurchaseRequest::productId)
                .toList();
        var storedProducts = productRepository.findAllByIdInOrderById(productIds);
        if (productIds.size() != storedProducts.size()) {
            throw new ProductPurchaseException("One or more products does not exist");
        }
        var sortedRequest = request
                .stream()
                .sorted(Comparator.comparing(ProductPurchaseRequest::productId))
                .toList();
        var purchasedProducts = new ArrayList<ProductPurchaseResponse>();
        for (int i = 0; i < storedProducts.size(); i++) {
            var product = storedProducts.get(i);
            var productRequest = sortedRequest.get(i);
            if (product.getAvailableQuantity() < productRequest.quantity()) {
                throw new ProductPurchaseException(
                        "Insufficient stock quantity for product with ID:: " + productRequest.productId());
            }
            var newAvailableQuantity = product.getAvailableQuantity() - productRequest.quantity();
            product.setAvailableQuantity(newAvailableQuantity);
            productRepository.save(product);
            purchasedProducts.add(productMapper.toproductPurchaseResponse(product, productRequest.quantity()));
        }
        return purchasedProducts;
    }
}
