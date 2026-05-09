package code.with.vanilson.productservice;

import code.with.vanilson.productservice.config.ProductSecurityConfig;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ProductNullException;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProductControllerTest — Unit Tests for ProductController
 * <p>
 * Uses @WebMvcTest to test only the web layer (controller).
 * Mocks the service layer via @MockBean.
 * This is a unit test — no database needed.
 */
@WebMvcTest(ProductController.class)
@Import(ProductSecurityConfig.class)
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private ProductMapper productMapper;

    @SuppressWarnings("unused")
    @MockBean
    TenantHibernateFilterActivator activator;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    private Product product;
    private ProductResponse productResponse;
    private ProductRequest productRequest;

    @BeforeEach
    public void setUp() throws Exception {
        // Set up test data
        product = new Product(1, "Product 1", "Description 1", 100.0, BigDecimal.valueOf(100.0));
        productResponse = new ProductResponse(1, "Product 1", "Description 1", 100.0, BigDecimal.valueOf(100.0), 1,
                "Category Name", "Category Description", "1");
        productRequest = new ProductRequest(1, "Product 1", "Description 1", 100.0, BigDecimal.valueOf(100.0), 1);

        // Mapper always returns the test product so service mocks receive a non-null Product
        when(productMapper.toProduct(any())).thenReturn(product);

        // Pass JWT filter through so it does not block the filter chain
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(
                    inv.<ServletRequest>getArgument(0),
                    inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }


    @DisplayName("Get all products - success")
    @Test
    public void testFindAllProducts() throws Exception {
        // Arrange
        List<ProductResponse> productList = List.of(productResponse);
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductResponse> expectedPage = new PageImpl<>(productList, pageable, productList.size());

        // Mock behavior for service to return Page of products
        // Use any(Pageable.class) to match ANY Pageable parameter the controller receives
        when(productService.getAllProducts(any(Pageable.class))).thenReturn(expectedPage);

        // Act: Perform GET request to /api/v1/products and verify results
        mockMvc.perform(get("/api/v1/products")
                        .header("X-Tenant-ID", "test-tenant-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Assert: Expect HTTP status 200 (OK)
                .andExpect(jsonPath("$.content[0].id").value(1)) // Assert: Verify first product's ID
                .andExpect(jsonPath("$.content[0].name").value("Product 1")); // Assert: Verify first product's name

        // Verify: Ensure productService.getAllProducts(pageable) was called
        verify(productService).getAllProducts(any(Pageable.class));
    }

    @Test
    @DisplayName("Test getProductById method - Product found Success")
    public void testGetProductById() throws Exception {
        when(productService.getProductById(1)).thenReturn(productResponse);

        mockMvc.perform(get("/api/v1/products/1")
                        .header("X-Tenant-ID", "test-tenant-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value(product.getName()));

        verify(productService).getProductById(anyInt());
    }

    @Test
    @DisplayName("Test getProductById method - Product not found")
    public void testGetProductById_NotFound() throws Exception {
        // Arrange
        int productId = 1;
        when(productService.getProductById(productId)).thenThrow(new ProductNotFoundException("Product not found", "product.not.found"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/products/{id}", productId)
                        .header("X-Tenant-ID", "test-tenant-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()); // Assert: Expect HTTP status 404 (Not Found)

        // Verify: Ensure productService.getProductById(productId) was called
        verify(productService).getProductById(productId);
    }

    @DisplayName("Test createProducts method - Success")
    @Test
    @WithMockUser(roles = "SELLER")
    public void testCreateProduct() throws Exception {
        when(productService.createProduct(any())).thenReturn(productRequest);

        mockMvc.perform(post("/api/v1/products/create")
                        .header("X-Tenant-ID", "test-tenant-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Product 1\",\"description\":\"Description 1\",\"availableQuantity\":100.0,\"price\":100.0,\"categoryId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(productRequest.name()));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    @DisplayName("Test createProducts method failed")
    public void testCreateProductReturnNull() throws Exception {
        // Mock ProductService behavior to throw ProductNullException
        when(productService.createProduct(any())).thenThrow(new ProductNullException("product cannot be null", "product.null"));

        // Perform POST request with invalid JSON payload (null product)
        mockMvc.perform(post("/api/v1/products/create")
                        .header("X-Tenant-ID", "test-tenant-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Product 1\",\"description\":\"Description 1\",\"price\":100.0}"))
                .andExpect(status().isBadRequest()); // Expect HTTP status 400 Bad Request
    }

    @Test
    @WithMockUser
    @DisplayName("Test purchaseProducts method - Success")
    public void testPurchaseProducts() throws Exception {
        // Arrange
        List<ProductPurchaseRequest> purchaseRequests = new ArrayList<>();
        purchaseRequests.add(new ProductPurchaseRequest(1, 5));
        purchaseRequests.add(new ProductPurchaseRequest(2, 10));
        List<ProductPurchaseResponse> purchaseResponses = Arrays.asList(
                new ProductPurchaseResponse(1, "Product 1", "Description 1", BigDecimal.valueOf(100.0), 1),
                new ProductPurchaseResponse(2, "Product 2", "Description 2", BigDecimal.valueOf(200.0), 2)
        );
        when(productService.purchaseProducts(purchaseRequests)).thenReturn(purchaseResponses);

        // Act & Assert
        mockMvc.perform(post("/api/v1/products/purchase")
                        .header("X-Tenant-ID", "test-tenant-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(purchaseRequests))) // Convert purchaseRequests to JSON string
                .andExpect(status().isOk()) // Assert: Expect HTTP status 200 (OK)
                .andExpect(jsonPath("$[0].productId").value(1)) // Assert: Verify first purchase response's product ID
                .andExpect(jsonPath("$[0].name").value("Product 1")) // Example of verifying name if it exists
                .andExpect(jsonPath("$[1].productId").value(2)) // Assert: Verify second purchase response's product ID
                .andExpect(jsonPath("$[1].name").value("Product 2")); // Example of verifying name if it exists

        // Verify: Ensure productService.purchaseProducts(purchaseRequests) was called
        verify(productService).purchaseProducts(purchaseRequests);
    }

    @Test
    @WithMockUser(roles = "SELLER")
    @DisplayName("Test updateProduct method - Success")
    public void testUpdateProduct() throws Exception {
        // Arrange
        int productId = 1;
        when(productService.updateProduct(anyInt(), any())).thenReturn(
                new ProductRequest(1, "Updated Product", "Updated Description", 15.0, BigDecimal.valueOf(150.0), 1));

        // Act & Assert
        mockMvc.perform(put("/api/v1/products/update/{id}", productId)
                        .header("X-Tenant-ID", "test-tenant-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Product\",\"description\":\"Updated Description\",\"availableQuantity\":15.0,\"price\":150.0,\"categoryId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Product"));

        verify(productService).updateProduct(anyInt(), any());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    @DisplayName("Test deleteProduct method - Success")
    public void testDeleteProduct() throws Exception {
        // Arrange
        int productId = 1;

        // Act & Assert
        mockMvc.perform(delete("/api/v1/products/delete/{id}", productId)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isNoContent()); // Assert: Expect HTTP status 204 (No Content)

        // Verify: Ensure productService.deleteProduct(productId) was called
        verify(productService).deleteProduct(productId);
    }

    @Test
    @WithMockUser(roles = "SELLER")
    @DisplayName("Delete products by id failed - Id Not found")
    public void testDeleteProductFailed() throws Exception {
        int productId = -1; // Using a non-existent ID

        // Mock ProductService to throw ProductNotFoundException when getProductById is called with a non-existent ID
        when(productService.getProductById(productId)).thenThrow(new ProductNotFoundException("Product not found", "product.not.found"));

        // Mock ProductService to throw ProductNotFoundException when deleteProduct is called with a non-existent ID
        doThrow(new ProductNotFoundException("Product not found", "product.not.found")).when(productService).deleteProduct(productId);

        // Perform DELETE request to delete a product with non-existent ID
        mockMvc.perform(delete("/api/v1/products/delete/{id}", productId)
                        .header("X-Tenant-ID", "test-tenant-123"))
                .andExpect(status().isNotFound()); // Assert: Expect HTTP status 404 (Not Found)

        // Verify: Ensure productService.deleteProduct(productId) was called
        verify(productService).deleteProduct(productId);
    }

    /**
     * This method takes an object obj, uses Jackson's ObjectMapper to convert it into a JSON string, and returns the JSON string
     *
     * @param obj objs
     * @return the JSON string
     */
    public static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
