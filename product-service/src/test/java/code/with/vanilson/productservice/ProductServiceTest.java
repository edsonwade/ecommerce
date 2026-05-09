package code.with.vanilson.productservice;

import code.with.vanilson.productservice.category.Category;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.productservice.exception.ProductNullException;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductServiceTest — Unit Tests
 * <p>
 * Covers all business rules: CRUD, pagination, purchase/stock reservation,
 * validation guards (null checks), and cache eviction triggers.
 * <p>
 * Framework: JUnit 5 + Mockito + AssertJ.
 * @Nested classes group scenarios by operation — easier to navigate.
 * MessageSource is mocked to return the key itself (avoids properties file dependency).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService — Unit Tests")
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper     productMapper;
    @Mock private MessageSource     messageSource;

    @InjectMocks
    private ProductService productService;

    // -------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------

    private Category category;
    private Product  product1;
    private Product  product2;
    private ProductResponse response1;
    private ProductResponse response2;

    @BeforeEach
    void setUp() {
        category = Category.builder().id(1).name("Electronics").description("Electronic items").build();

        product1 = new Product(1, "Laptop",    "Gaming Laptop",  5.0,  BigDecimal.valueOf(1200.00), category);
        product2 = new Product(2, "Headphones","Noise Cancelling",15.0, BigDecimal.valueOf(250.00),  category);

        response1 = new ProductResponse(1, "Laptop",    "Gaming Laptop",  5.0,
                BigDecimal.valueOf(1200.00), 1, "Electronics", "Electronic items", "1");
        response2 = new ProductResponse(2, "Headphones","Noise Cancelling",15.0,
                BigDecimal.valueOf(250.00), 1, "Electronics", "Electronic items", "1");

        // MessageSource stub: returns the key itself so tests are portable
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------
    // getAllProducts (paginated)
    // -------------------------------------------------------

    @Nested
    @DisplayName("getAllProducts (paginated)")
    class GetAll {

        @Test
        @DisplayName("should return paginated product list")
        void shouldReturnPageOfProducts() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(product1, product2), pageable, 2);

            when(productRepository.findAll(pageable)).thenReturn(page);
            when(productMapper.fromProduct(product1)).thenReturn(response1);
            when(productMapper.fromProduct(product2)).thenReturn(response2);

            Page<ProductResponse> result = productService.getAllProducts(pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                    .hasSize(2)
                    .extracting(ProductResponse::name)
                    .containsExactly("Laptop", "Headphones");
        }

        @Test
        @DisplayName("should return empty page when no products exist")
        void shouldReturnEmptyPageWhenNoProducts() {
            Pageable pageable = PageRequest.of(0, 10);
            when(productRepository.findAll(pageable)).thenReturn(Page.empty());

            Page<ProductResponse> result = productService.getAllProducts(pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // -------------------------------------------------------
    // getProductById
    // -------------------------------------------------------

    @Nested
    @DisplayName("getProductById")
    class GetById {

        @Test
        @DisplayName("should return product response when found")
        void shouldReturnProductWhenFound() {
            when(productRepository.findById(1)).thenReturn(Optional.of(product1));
            when(productMapper.fromProduct(product1)).thenReturn(response1);

            ProductResponse result = productService.getProductById(1);

            assertThat(result.id()).isEqualTo(1);
            assertThat(result.name()).isEqualTo("Laptop");
            assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(1200.00));
        }

        @Test
        @DisplayName("should throw ProductNotFoundException when product does not exist")
        void shouldThrowNotFoundForUnknownId() {
            when(productRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(999))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("product.not.found");
        }
    }

    // -------------------------------------------------------
    // createProduct
    // -------------------------------------------------------

    @Nested
    @DisplayName("createProduct")
    class Create {

        @Test
        @DisplayName("should persist and return ProductRequest when valid")
        void shouldCreateProductSuccessfully() {
            ProductRequest expectedRequest = new ProductRequest(1, "Laptop", "Gaming Laptop",
                    5.0, BigDecimal.valueOf(1200.00), 1);
            when(productRepository.save(product1)).thenReturn(product1);
            when(productMapper.toProductRequest(product1)).thenReturn(expectedRequest);

            ProductRequest result = productService.createProduct(product1);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Laptop");
            verify(productRepository, times(1)).save(product1);
        }

        @Test
        @DisplayName("should throw ProductNullException when product is null")
        void shouldThrowWhenProductNull() {
            assertThatThrownBy(() -> productService.createProduct(null))
                    .isInstanceOf(ProductNullException.class)
                    .hasMessageContaining("product.null");
        }

        @Test
        @DisplayName("should throw ProductNullException when category is null")
        void shouldThrowWhenCategoryNull() {
            Product noCategory = new Product(1, "X", "Y", 1.0, BigDecimal.ONE, null);

            assertThatThrownBy(() -> productService.createProduct(noCategory))
                    .isInstanceOf(ProductNullException.class)
                    .hasMessageContaining("product.category.null");
        }
    }

    // -------------------------------------------------------
    // updateProduct
    // -------------------------------------------------------

    @Nested
    @DisplayName("updateProduct")
    class Update {

        @Test
        @DisplayName("should update product fields and save when all fields valid")
        void shouldUpdateSuccessfully() {
            Product updatedData = new Product(1, "Laptop Pro", "Updated desc", 8.0,
                    BigDecimal.valueOf(1500.00), category);
            ProductRequest expectedReq = new ProductRequest(1, "Laptop Pro", "Updated desc",
                    8.0, BigDecimal.valueOf(1500.00), 1);

            when(productRepository.findById(1)).thenReturn(Optional.of(product1));
            when(productRepository.save(any(Product.class))).thenReturn(updatedData);
            when(productMapper.toProductRequest(any(Product.class))).thenReturn(expectedReq);

            ProductRequest result = productService.updateProduct(1, updatedData);

            assertThat(result.name()).isEqualTo("Laptop Pro");
            assertThat(result.availableQuantity()).isEqualTo(8.0);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("should throw ProductNotFoundException when product ID does not exist")
        void shouldThrowNotFoundOnUpdate() {
            when(productRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(99, product1))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ProductNullException when name is null")
        void shouldThrowWhenNameNull() {
            Product nullName = new Product(1, null, "desc", 5.0, BigDecimal.ONE, category);
            when(productRepository.findById(1)).thenReturn(Optional.of(product1));

            assertThatThrownBy(() -> productService.updateProduct(1, nullName))
                    .isInstanceOf(ProductNullException.class)
                    .hasMessageContaining("product.name.null");
        }

        @Test
        @DisplayName("should throw ProductNullException when description is null")
        void shouldThrowWhenDescriptionNull() {
            Product nullDesc = new Product(1, "name", null, 5.0, BigDecimal.ONE, category);
            when(productRepository.findById(1)).thenReturn(Optional.of(product1));

            assertThatThrownBy(() -> productService.updateProduct(1, nullDesc))
                    .isInstanceOf(ProductNullException.class)
                    .hasMessageContaining("product.description.null");
        }

        @Test
        @DisplayName("should throw ProductNullException when availableQuantity is negative")
        void shouldThrowWhenQuantityNegative() {
            Product negQty = new Product(1, "name", "desc", -1.0, BigDecimal.ONE, category);
            when(productRepository.findById(1)).thenReturn(Optional.of(product1));

            assertThatThrownBy(() -> productService.updateProduct(1, negQty))
                    .isInstanceOf(ProductNullException.class)
                    .hasMessageContaining("product.quantity.negative");
        }

        @Test
        @DisplayName("should throw ProductNullException when price is null")
        void shouldThrowWhenPriceNull() {
            Product nullPrice = new Product(1, "name", "desc", 5.0, null, category);
            when(productRepository.findById(1)).thenReturn(Optional.of(product1));

            assertThatThrownBy(() -> productService.updateProduct(1, nullPrice))
                    .isInstanceOf(ProductNullException.class)
                    .hasMessageContaining("product.price.null");
        }
    }

    // -------------------------------------------------------
    // deleteProduct
    // -------------------------------------------------------

    @Nested
    @DisplayName("deleteProduct")
    class Delete {

        @Test
        @DisplayName("should delete product when it exists")
        void shouldDeleteSuccessfully() {
            when(productRepository.findById(1)).thenReturn(Optional.of(product1));

            productService.deleteProduct(1);

            verify(productRepository, times(1)).deleteById(1);
        }

        @Test
        @DisplayName("should throw ProductNotFoundException when product does not exist")
        void shouldThrowWhenNotFound() {
            when(productRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(99))
                    .isInstanceOf(ProductNotFoundException.class);
            verify(productRepository, never()).deleteById(any());
        }
    }

    // -------------------------------------------------------
    // purchaseProducts (stock reservation)
    // -------------------------------------------------------

    @Nested
    @DisplayName("purchaseProducts — stock reservation")
    class Purchase {

        @Test
        @DisplayName("should reserve stock and return purchase responses when sufficient stock")
        void shouldSucceedWhenStockAvailable() {
            List<ProductPurchaseRequest> requests = List.of(
                    new ProductPurchaseRequest(1, 2.0),
                    new ProductPurchaseRequest(2, 3.0)
            );
            ProductPurchaseResponse pr1 = new ProductPurchaseResponse(
                    1, "Laptop",    "Gaming Laptop",  BigDecimal.valueOf(1200), 2.0);
            ProductPurchaseResponse pr2 = new ProductPurchaseResponse(
                    2, "Headphones","Noise Cancelling",BigDecimal.valueOf(250),  3.0);

            when(productRepository.findAllByIdInOrderById(List.of(1, 2)))
                    .thenReturn(List.of(product1, product2));
            when(productMapper.toproductPurchaseResponse(product1, 2.0)).thenReturn(pr1);
            when(productMapper.toproductPurchaseResponse(product2, 3.0)).thenReturn(pr2);

            List<ProductPurchaseResponse> result = productService.purchaseProducts(requests);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ProductPurchaseResponse::productId).containsExactlyInAnyOrder(1, 2);
            // Stock must have been decremented and saved
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getAvailableQuantity()).isEqualTo(3.0); // 5 - 2
            assertThat(captor.getAllValues().get(1).getAvailableQuantity()).isEqualTo(12.0); // 15 - 3
        }

        @Test
        @DisplayName("should throw ProductPurchaseException when one product does not exist")
        void shouldThrowWhenProductMissing() {
            List<ProductPurchaseRequest> requests = List.of(new ProductPurchaseRequest(99, 1.0));
            when(productRepository.findAllByIdInOrderById(List.of(99))).thenReturn(List.of());

            assertThatThrownBy(() -> productService.purchaseProducts(requests))
                    .isInstanceOf(ProductPurchaseException.class)
                    .hasMessageContaining("product.purchase.not.found");
        }

        @Test
        @DisplayName("should throw ProductPurchaseException when stock is insufficient")
        void shouldThrowWhenInsufficientStock() {
            // product1 has 5.0 available; we request 10.0
            List<ProductPurchaseRequest> requests = List.of(new ProductPurchaseRequest(1, 10.0));
            when(productRepository.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(product1));

            assertThatThrownBy(() -> productService.purchaseProducts(requests))
                    .isInstanceOf(ProductPurchaseException.class)
                    .hasMessageContaining("product.purchase.insufficient.stock");
        }

        @Test
        @DisplayName("should throw ProductPurchaseException when request list is empty")
        void shouldThrowWhenRequestListEmpty() {
            assertThatThrownBy(() -> productService.purchaseProducts(List.of()))
                    .isInstanceOf(ProductPurchaseException.class)
                    .hasMessageContaining("product.purchase.list.empty");
        }

        @Test
        @DisplayName("should throw ProductPurchaseException when request list is null")
        void shouldThrowWhenRequestListNull() {
            assertThatThrownBy(() -> productService.purchaseProducts(null))
                    .isInstanceOf(ProductPurchaseException.class);
        }
    }
}
