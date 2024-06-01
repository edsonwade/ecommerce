package code.with.vanilson.productservice;

import code.with.vanilson.productservice.exception.ProductBadRequestException;
import code.with.vanilson.productservice.exception.ProductNullException;
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

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper();
    }

    @Test
    @DisplayName("Convert Product to ProductRequest - Expect success")
    void testToProductRequest_Success() {
        // Arrange
        Product product = new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0));

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
        products.add(new Product(1, "Product 1", "Description 1", 10.0, BigDecimal.valueOf(100.0)));
        products.add(new Product(2, "Product 2", "Description 2", 20.0, BigDecimal.valueOf(200.0)));

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
        products.add(new Product(1, "Product 1", "Description 1", 10.0, BigDecimal.valueOf(100.0)));
        products.add(new Product(2, "Product 2", "Description 2", 20.0, BigDecimal.valueOf(200.0)));

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
    @DisplayName("Convert Product to ProductResponse - Expect success")
    void testFromProduct_Success() {
        // Arrange
        Product product = new Product(1, "Product Name", "Product Description", 10.0, BigDecimal.valueOf(100.0));

        // Act
        ProductResponse productResponse = productMapper.fromProduct(product);

        // Assert
        assertEquals(product.getId(), productResponse.id());
        assertEquals(product.getName(), productResponse.name());
        assertEquals(product.getDescription(), productResponse.description());
        assertEquals(product.getAvailableQuantity(), productResponse.availableQuantity());
        assertEquals(product.getPrice(), productResponse.price());
    }

}
