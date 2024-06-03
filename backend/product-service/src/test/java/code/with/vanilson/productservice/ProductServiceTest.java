package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ProductNullException;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    /**
     * Repository for accessing product data from the database.
     */
    private ProductRepository productRepository;

    /**
     * Mapper for converting between Product and ProductRequest/ProductResponse objects.
     */
    private ProductMapper productMapper;

    /**
     * Service for handling business logic related to products.
     */
    private ProductService productService;

    private final Category category = new Category();

    /**
     * Initializes the test environment before each test method execution.
     * Configures mock objects and injects them into the ProductService instance.
     */
    @BeforeEach
    public void setUp() {
        // Mock the ProductRepository and ProductMapper objects
        productRepository = mock(ProductRepository.class);
        productMapper = mock(ProductMapper.class);

        // Create a ProductService instance with mocked dependencies
        productService = new ProductService(productRepository, productMapper);

        // Initialize the Mockito mocks
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Test getAllProducts method - Expect success")
    public void testGetAllProducts() {
        // Arrange
        List<Product> productList = new ArrayList<>();
        productList.add(new Product(1, "Product 1", "Description 1", 10.0, BigDecimal.valueOf(100.0)));
        productList.add(new Product(2, "Product 2", "Description 2", 20.0, BigDecimal.valueOf(200.0)));

        when(productRepository.findAll()).thenReturn(productList);

        List<ProductResponse> expectedProductResponses = new ArrayList<>();
        expectedProductResponses.add(
                new ProductResponse(1, "Product 1", "Description 1", 10.0, BigDecimal.valueOf(100.0), 1,
                        "Game", "Game for kids"));
        expectedProductResponses.add(
                new ProductResponse(2, "Product 2", "Description 2", 20.0, BigDecimal.valueOf(200.0),
                        2,
                        "PC", "Apple Mac12"));

        when(productMapper.toProductResponse(productList)).thenReturn(expectedProductResponses);

        // Act
        var actualProductResponses = productService.getAllProducts();

        // Assert
        assertEquals(expectedProductResponses.size(),
                actualProductResponses.size());
        IntStream.range(0, expectedProductResponses.size())
                .forEachOrdered(i -> assertEquals(expectedProductResponses.get(i), actualProductResponses.get(i)));

        verify(productRepository, times(1)).findAll();
        verify(productMapper, times(1)).toProductResponse(productList);
    }

    @Test
    @DisplayName("Test getProductById method - Success")
    public void testGetProductById_Success() {
        // Arrange
        Product product = new Product(1, "Product Name", "Description", 10.0, BigDecimal.valueOf(100.0)
        );
        ProductResponse expectedResponse =
                new ProductResponse(1, "Product Name", "Description", 10.0, BigDecimal.valueOf(100.0), 1, "Game",
                        "Game for kids");

        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(productMapper.fromProduct(product)).thenReturn(expectedResponse);

        // Act
        Optional<ProductResponse> result = productService.getProductById(1);

        // Assert
        assertEquals(expectedResponse, result.get());
    }

    @Test
    @DisplayName("Test getProductById method - Failure")
    public void testGetProductById_Failure() {
        // Arrange
        when(productRepository.findById(1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProductNotFoundException.class, () -> productService.getProductById(1));
    }

    @Test
    @DisplayName("Test createProduct method")
    public void testCreateProduct() {
        // Arrange

        Product product =
                new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), category);
        Product savedProduct =
                new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), category);
        ProductRequest productRequest =
                new ProductRequest(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), 1);

        when(productRepository.save(product)).thenReturn(savedProduct);
        when(productMapper.toProductRequest(savedProduct)).thenReturn(productRequest);

        // Act
        ProductRequest result = productService.createProduct(product);

        // Assert
        assertEquals(productRequest, result);
        verify(productRepository, times(1)).save(product);
        verify(productMapper, times(1)).toProductRequest(savedProduct);
    }

    @Test
    @DisplayName("Test createProduct method throws ProductNullException with null product")
    public void testCreateProductThrowsProductNullExceptionWithNullProduct() {
        // Arrange
        Product product = null;
        // Act & Assert
        assertThrows(ProductNullException.class, () -> productService.createProduct(product));
    }


    /*
    if (product.getCategory() == null) {
            throw new ProductNullException("Product category must not be null");
        }
     */


    @Test
    @DisplayName("Test createProduct method throws ProductNullException with null category")
    public void testCreateProductThrowsProductNullExceptionWithNullCategory() {
        // Arrange
        Product product = new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), null);
        // Act & Assert
        assertThrows(ProductNullException.class, () -> productService.createProduct(product));
    }

    @Test
    void testCreateProducts_Success() {
        // Arrange
        List<Product> products = Arrays.asList(
                new Product(1, "Product 1", "Description 1", 10, BigDecimal.valueOf(100)),
                new Product(2, "Product 2", "Description 2", 20, BigDecimal.valueOf(200))
        );
        List<ProductRequest> productRequests = Arrays.asList(
                new ProductRequest(1, "Product 1", "Description 1", 10.0, BigDecimal.valueOf(100), 1),
                new ProductRequest(2, "Product 2", "Description 2", 20.0, BigDecimal.valueOf(200), 2)
        );
        when(productRepository.saveAll(products)).thenReturn(products);
        when(productMapper.toProductRequests(products)).thenReturn(productRequests);

        // Act
        List<ProductRequest> result = productService.createProducts(products);

        // Assert
        assertEquals(productRequests.size(), result.size());
        for (int i = 0; i < productRequests.size(); i++) {
            assertEquals(productRequests.get(i), result.get(i));
        }
    }

    @Test
    void testCreateProducts_NullList() {
        // Act & Assert
        assertThrows(ProductNullException.class, () -> productService.createProducts(null));
    }

    @Test
    void testCreateProducts_EmptyList() {
        // Act & Assert
        assertThrows(ProductNullException.class, () -> productService.createProducts(List.of()));
    }

    @Test
    public void testUpdateProduct() {
        // Arrange
        int productId = 1;
        Product productRequest =
                new Product(productId, "Updated Name", "Updated Description", 20.0, BigDecimal.valueOf(200.0));
        Product existingProduct =
                new Product(productId, "Original Name", "Original Description", 10.0, BigDecimal.valueOf(100.0));
        Product updatedProduct =
                new Product(productId, "Updated Name", "Updated Description", 20.0, BigDecimal.valueOf(200.0));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenReturn(updatedProduct);
        when(productMapper.toProductRequest(updatedProduct)).thenReturn(
                new ProductRequest(productId, "Updated Name", "Updated Description", 20.0, BigDecimal.valueOf(200.0),
                        1));

        // Act
        var result = productService.updateProduct(productId, productRequest);

        // Assert
        assertEquals("Updated Name", result.name());
        assertEquals("Updated Description", result.description());
        assertEquals(20.0, result.availableQuantity(), 10.0);
        assertEquals(BigDecimal.valueOf(200.0), result.price());
    }

    @DisplayName("expected = ProductNotFoundException.class")
    @Test
    public void testUpdateProduct_ProductNotFound() {
        // Arrange
        int productId = 1;
        Product product =
                new Product(productId, "Updated Name",
                        "Updated Description",
                        20.0,
                        BigDecimal.valueOf(200.0));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // Act
        assertThrows(ProductNotFoundException.class, () -> productService.updateProduct(productId, product));
    }

    @Test
    @DisplayName("Test deleteProduct method - Success")
    public void testDeleteProduct() {
        // Arrange
        int productId = 1;
        Product productToDelete =
                new Product(productId, "Test Product", "Test Description", 10.0, BigDecimal.valueOf(100.0));
        when(productRepository.findById(productId)).thenReturn(Optional.of(productToDelete));

        // Act
        productService.deleteProduct(productId);

        // Assert
        verify(productRepository, times(1)).deleteById(productId);
    }

    @Test
    @DisplayName("Test deleteProduct method when product is not found - Expect ProductNotFoundException")
    public void testDeleteProduct_ProductNotFound() {
        // Arrange
        int productId = 1;
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProductNotFoundException.class, () -> productService.deleteProduct(productId));

    }

    @Test
    public void testUpdateProduct_NullName() {
        // Arrange
        int productId = 1;
        Product productRequest = new Product(productId, null, "Description", 10.0, BigDecimal.valueOf(100.0));
        Product existingProduct =
                new Product(productId, "Existing Name", "Existing Description", 10.0, BigDecimal.valueOf(100.0));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productService.updateProduct(productId, productRequest));
    }

    @Test
    public void testUpdateProduct_NullDescription() {
        // Arrange
        int productId = 1;
        Product productRequest = new Product(productId, "Name", null, 10.0, BigDecimal.valueOf(100.0));
        Product existingProduct =
                new Product(productId, "Existing Name", "Existing Description", 10.0, BigDecimal.valueOf(100.0));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productService.updateProduct(productId, productRequest));
    }

    @Test
    public void testUpdateProduct_NegativeQuantity() {
        // Arrange
        int productId = 1;
        Product productRequest = new Product(productId, "Name", "Description", -10.0, BigDecimal.valueOf(100.0));
        Product existingProduct =
                new Product(productId, "Existing Name", "Existing Description", 10.0, BigDecimal.valueOf(100.0));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productService.updateProduct(productId, productRequest));
    }

    @Test
    public void testUpdateProduct_NullPrice() {
        // Arrange
        int productId = 1;
        Product productRequest = new Product(productId, "Name", "Description", 10.0, null);
        Product existingProduct =
                new Product(productId, "Existing Name", "Existing Description", 10.0, BigDecimal.valueOf(100.0));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productService.updateProduct(productId, productRequest));
    }


    @Test
    @DisplayName("Test purchaseProducts method - Success")
    public void testPurchaseProducts_Success() {
        // Arrange
        List<ProductPurchaseRequest> requests = new ArrayList<>();
        requests.add(new ProductPurchaseRequest(1, 1)); // Product with ID 1, Quantity: 1
        requests.add(new ProductPurchaseRequest(2, 1)); // Product with ID 2, Quantity: 1

        Iterable<Integer> expectedProductIds = List.of(1, 2); // Expected product IDs

        // Define the expected products
        List<Product> storedProducts = new ArrayList<>();
        storedProducts.add(new Product(1, "Product 1", "Description 1", 10, BigDecimal.valueOf(100)));
        storedProducts.add(new Product(2, "Product 2", "Description 2", 20, BigDecimal.valueOf(200)));

        // Mock behavior of productRepository
        when(productRepository.findAllByIdInOrderById(any())).thenAnswer(invocation -> {
            Iterable<Integer> ids = invocation.getArgument(0);
            // Check if the provided IDs match the expected IDs
            assertEquals(expectedProductIds, ids);
            return storedProducts;
        });

        // Mock behavior of productMapper
        when(productMapper.toproductPurchaseResponse(any(), anyDouble())).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            double quantity = invocation.getArgument(1);
            // Create and return a dummy ProductPurchaseResponse
            return new ProductPurchaseResponse(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    quantity
            );
        });

        // Act
        List<ProductPurchaseResponse> response = productService.purchaseProducts(requests);

        // Assert
        Assertions.assertNotNull(response);
        assertEquals(requests.size(), response.size());
        // Assert other conditions if necessary
    }


    @Test
    @DisplayName("Test purchaseProducts method - Products Not Found")
    public void testPurchaseProducts_ProductsNotFound() {
        // Arrange
        List<ProductPurchaseRequest> requests = new ArrayList<>();
        requests.add(new ProductPurchaseRequest(1, 1)); // Product with ID 1, Quantity: 1
        requests.add(new ProductPurchaseRequest(2, 1)); // Product with ID 2, Quantity: 1

        Iterable<Integer> expectedProductIds = List.of(1, 2); // Expected product IDs

        // Mock behavior of productRepository to return an empty list (no products found)
        when(productRepository.findAllByIdInOrderById(any())).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThrows(ProductPurchaseException.class, () -> productService.purchaseProducts(requests));
    }

    @Test
    @DisplayName("Test purchaseProducts method - Insufficient Stock")
    public void testPurchaseProducts_InsufficientStock() {
        // Arrange
        List<Product> storedProducts = new ArrayList<>();
        storedProducts.add(new Product(2, "Product 2", "Description 2", 5, BigDecimal.valueOf(200))); // Insufficient stock

        // Mock behavior of productRepository
        when(productRepository.findAllByIdInOrderById(any())).thenReturn(storedProducts);

        // Act & Assert
        assertThrows(ProductPurchaseException.class, () -> productService.purchaseProducts(new ArrayList<>()),
                "Insufficient stock quantity for product");
    }

    @Test
    @DisplayName("Test purchaseProducts method - Insufficient Stock")
    public void testPurchaseProducts_InsufficientStocks() {
        // Arrange
        List<ProductPurchaseRequest> requests = new ArrayList<>();
        requests.add(new ProductPurchaseRequest(2, 10)); // Product with ID 2, Quantity: 10 (exceeding available stock)

        // Define the expected stored products
        List<Product> storedProducts = new ArrayList<>();
        storedProducts.add(new Product(2, "Product 2", "Description 2", 5, BigDecimal.valueOf(200))); // Insufficient stock

        // Mock behavior of productRepository
        when(productRepository.findAllByIdInOrderById(any())).thenReturn(storedProducts);

        // Act & Assert
        assertThrows(ProductPurchaseException.class, () -> productService.purchaseProducts(requests),
                "Insufficient stock quantity for product with ID:: 2");
    }




}