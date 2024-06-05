package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.except.ProductBadRequestException;
import code.with.vanilson.productservice.except.ProductNullException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductMapperTest {

    private ProductMapper productMapper;

    private Category category;

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper();
        category = new Category(1, "Game", "Game for kids");
    }

    @Test
    @DisplayName("Convert Product to ProductRequest - Expect success")
    void testToProductRequest_Success() {
        // Arrange
        Product product =
                new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), category);

        // Act
        ProductRequest productRequest = productMapper.toProductRequest(product);

        // Assert
        assertEquals(product.getId(), productRequest.id());
        assertEquals(product.getName(), productRequest.name());
        assertEquals(product.getDescription(), productRequest.description());
        assertEquals(product.getAvailableQuantity(), productRequest.availableQuantity());
        assertEquals(product.getPrice(), productRequest.price());
    }

    @Test
    @DisplayName("Convert Product to ProductResponse - Expect success")
    void testToProductResponse_Success() {
        // Arrange
        List<Product> products = new ArrayList<>();
        products.add(new Product(1, "Product 1", "Description 1", 10.0, BigDecimal.valueOf(100.0), category));
        products.add(new Product(2, "Product 2", "Description 2", 20.0, BigDecimal.valueOf(200.0), category));

        // Act
        List<ProductResponse> productResponses = productMapper.toProductResponse(products);

        // Assert
        assertEquals(products.size(), productResponses.size());
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            ProductResponse productResponse = productResponses.get(i);
            assertEquals(product.getId(), productResponse.id());
            assertEquals(product.getName(), productResponse.name());
            assertEquals(product.getDescription(), productResponse.description());
            assertEquals(product.getAvailableQuantity(), productResponse.availableQuantity());
            assertEquals(product.getPrice(), productResponse.price());
        }
    }

    @Test
    @DisplayName("Convert List of Products to List of ProductRequests - Expect success")
    void testToProductRequests_Success() {
        // Arrange
        List<Product> products = new ArrayList<>();
        products.add(new Product(1, "Product 1", "Description 1", 10.0, BigDecimal.valueOf(100.0), category));
        products.add(new Product(2, "Product 2", "Description 2", 20.0, BigDecimal.valueOf(200.0), category));

        // Act
        List<ProductRequest> productRequests = productMapper.toProductRequests(products);

        // Assert
        assertEquals(products.size(), productRequests.size());
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            ProductRequest productRequest = productRequests.get(i);
            assertEquals(product.getId(), productRequest.id());
            assertEquals(product.getName(), productRequest.name());
            assertEquals(product.getDescription(), productRequest.description());
            assertEquals(product.getAvailableQuantity(), productRequest.availableQuantity());
            assertEquals(product.getPrice(), productRequest.price());
        }
    }

    @Test
    @DisplayName("Convert null Product to ProductResponse - Expect failure")
    void testFromProduct_NullProduct_ThrowsException() {
        // Act & Assert
        assertThrows(ProductBadRequestException.class, () -> productMapper.fromProduct(null));
    }

    @Test
    @DisplayName("Validate Product - Missing ID - Expect failure")
    void testValidateProduct_MissingId_ThrowsException() {
        // Arrange
        Product product = new Product(null, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productMapper.validateProduct(product));
    }

    @Test
    @DisplayName("Validate Product - Missing Name - Expect failure")
    void testValidateProduct_MissingName_ThrowsException() {
        // Arrange
        Product product = new Product(1, null, "Product Description", 10.0, BigDecimal.valueOf(100.0));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productMapper.validateProduct(product));
    }

    @Test
    @DisplayName("Validate Product - Missing Description - Expect failure")
    void testValidateProduct_MissingDescription_ThrowsException() {
        // Arrange
        Product product = new Product(1, "Product Name", null, 10.0, BigDecimal.valueOf(100.0));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productMapper.validateProduct(product));
    }

    @Test
    @DisplayName("Validate Product - Negative Available Quantity - Expect failure")
    void testValidateProduct_NegativeQuantity_ThrowsException() {
        // Arrange
        Product product = new Product(1, "Product Name", "Product Description", -1.0, BigDecimal.valueOf(100.0));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productMapper.validateProduct(product));
    }

    @Test
    @DisplayName("Validate Product - Missing Price - Expect failure")
    void testValidateProduct_MissingPrice_ThrowsException() {
        // Arrange
        Product product = new Product(1, "Product Name", "Product Description", 10.0, null);

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productMapper.validateProduct(product));
    }


    @Test
    @DisplayName("Validate Product - Missing Category - Expect failure")
    void testValidateProduct_MissingCategory_ThrowsException() {
        // Arrange
        Product product = new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), null);

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productMapper.validateProduct(product));
    }

    @Test
    @DisplayName("Convert Product to ProductResponse - Expect success")
    void testFromProduct_Success() {
        // Arrange
        Product product =
                new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), category);

        // Act
        ProductResponse productResponse = productMapper.fromProduct(product);

        // Assert
        assertEquals(product.getId(), productResponse.id());
        assertEquals(product.getName(), productResponse.name());
        assertEquals(product.getDescription(), productResponse.description());
        assertEquals(product.getAvailableQuantity(), productResponse.availableQuantity());
        assertEquals(product.getPrice(), productResponse.price());
    }

    @Test
    @DisplayName("Convert Product to ProductResponse - Expect success")
    void testToProductResponse_Success_V1() {
        // Arrange
        Category category = new Category(1, "Game", "Game for kids");
        Product product = new Product(1, "Game", "Game for kids", 10.0, BigDecimal.valueOf(100.0), category);

        // Act
        ProductResponse productResponse = productMapper.toProductResp(product);

        // Assert
        assertEquals(product.getId(), productResponse.id());
        assertEquals(product.getName(), productResponse.name());
        assertEquals(product.getDescription(), productResponse.description());
        assertEquals(product.getAvailableQuantity(), productResponse.availableQuantity());
        assertEquals(product.getPrice(), productResponse.price());
        assertEquals(product.getCategory().getId(), productResponse.categoryId());
        assertEquals(product.getCategory().getName(), productResponse.categoryName());
        assertEquals(product.getCategory().getDescription(), productResponse.categoryDescription());
    }

    @Test
    @DisplayName("Convert Product to ProductResponse - Null Category - Expect ProductNullException")
    void testToProductResponse_NullCategory_ThrowsException() {
        // Arrange
        Product product = new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0));

        // Act & Assert
        assertThrows(ProductNullException.class, () -> productMapper.toProductResp(product));
    }

    @Test
    @DisplayName("Convert Product to ProductPurchaseResponse - Expect success")
    void testToproductPurchaseResponse_Success() {
        // Arrange
        Category category = new Category(1, "Game", "Game for kids");
        Product product =
                new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), category);
        double quantity = 5.0;

        // Act
        ProductPurchaseResponse purchaseResponse = productMapper.toproductPurchaseResponse(product, quantity);

        // Assert
        assertEquals(product.getId(), purchaseResponse.productId());
        assertEquals(product.getName(), purchaseResponse.name());
        assertEquals(product.getDescription(), purchaseResponse.description());
        assertEquals(product.getPrice(), purchaseResponse.price());
        assertEquals(quantity, purchaseResponse.quantity());
    }

    @Test
    @DisplayName("Convert ProductRequest to Product - Expect success")
    void testToProduct_Success() {
        // Arrange
        ProductRequest request =
                new ProductRequest(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), 1);

        // Act
        Product product = productMapper.toProduct(request);

        // Assert
        assertEquals(request.id(), product.getId());
        assertEquals(request.name(), product.getName());
        assertEquals(request.description(), product.getDescription());
        assertEquals(request.availableQuantity(), product.getAvailableQuantity());
        assertEquals(request.price(), product.getPrice());
        assertEquals(request.categoryId(), product.getCategory().getId());
    }

    @Test
    @DisplayName("Convert Product to ProductResponse - Expect success")
    void testToProductResponse_Success_V2() {
        // Arrange
        Category category = new Category(1, "Game", "Game for kids");
        Product product =
                new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0), category);

        // Act
        ProductResponse productResponse = productMapper.toProductResponse(product);

        // Assert
        assertEquals(product.getId(), productResponse.id());
        assertEquals(product.getName(), productResponse.name());
        assertEquals(product.getDescription(), productResponse.description());
        assertEquals(product.getAvailableQuantity(), productResponse.availableQuantity());
        assertEquals(product.getPrice(), productResponse.price());
        assertEquals(product.getCategory().getId(), productResponse.categoryId());
        assertEquals(product.getCategory().getName(), productResponse.categoryName());
        assertEquals(product.getCategory().getDescription(), productResponse.categoryDescription());
    }
}
