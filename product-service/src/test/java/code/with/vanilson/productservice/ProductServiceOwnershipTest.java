package code.with.vanilson.productservice;

import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import code.with.vanilson.productservice.category.Category;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService — Ownership Tests")
class ProductServiceOwnershipTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper     productMapper;
    @Mock private MessageSource     messageSource;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(long userId, String role) {
        var principal = new SecurityPrincipal("u@x.com", userId, "t1", role);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Product productOwnedBy(Integer id, String ownerId) {
        Product p = new Product();
        p.setId(id);
        p.setName("Widget");
        p.setDescription("A widget");
        p.setAvailableQuantity(10.0);
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setCategory(Category.builder().id(1).name("Electronics").description("Devices").build());
        p.setCreatedBy(ownerId);
        return p;
    }

    // -------------------------------------------------------
    // createProduct — ownership stamping
    // -------------------------------------------------------

    @Nested
    @DisplayName("createProduct — ownership stamping")
    class CreateOwnership {

        @Test
        @DisplayName("stamps createdBy from SecurityContext principal userId")
        void stamps_createdBy_from_principal() {
            authAs(42L, "SELLER");
            Product p = productOwnedBy(0, null);
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductRequest(any())).thenReturn(
                    new ProductRequest(0, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99), 1));

            productService.createProduct(p);

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo("42");
        }

        @Test
        @DisplayName("stamps createdBy as 'system' when no principal in context")
        void stamps_system_when_no_principal() {
            Product p = productOwnedBy(0, null);
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductRequest(any())).thenReturn(
                    new ProductRequest(0, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99), 1));

            productService.createProduct(p);

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo("system");
        }
    }

    // -------------------------------------------------------
    // updateProduct — ownership guard
    // -------------------------------------------------------

    @Nested
    @DisplayName("updateProduct — ownership guard")
    class UpdateOwnership {

        @Test
        @DisplayName("SELLER can update their own product")
        void seller_can_update_own_product() {
            authAs(7L, "SELLER");
            Product existing = productOwnedBy(1, "7");
            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductRequest(any())).thenReturn(
                    new ProductRequest(1, "Updated", "Desc", 5.0, BigDecimal.ONE, 1));

            Product update = new Product(1, "Updated", "Desc", 5.0, BigDecimal.ONE);
            productService.updateProduct(1, update);

            verify(productRepository).save(existing);
            assertThat(existing.getUpdatedBy()).isEqualTo("7");
        }

        @Test
        @DisplayName("SELLER cannot update another SELLER's product")
        void seller_cannot_update_other_product() {
            authAs(7L, "SELLER");
            Product existing = productOwnedBy(1, "99"); // owned by user 99
            when(productRepository.findById(1)).thenReturn(Optional.of(existing));

            Product update = new Product(1, "Updated", "Desc", 5.0, BigDecimal.ONE);

            assertThatThrownBy(() -> productService.updateProduct(1, update))
                    .isInstanceOf(ProductForbiddenException.class);
        }

        @Test
        @DisplayName("ADMIN can update any product regardless of owner")
        void admin_can_update_any_product() {
            authAs(1L, "ADMIN");
            Product existing = productOwnedBy(1, "99");
            when(productRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductRequest(any())).thenReturn(
                    new ProductRequest(1, "Updated", "Desc", 5.0, BigDecimal.ONE, 1));

            Product update = new Product(1, "Updated", "Desc", 5.0, BigDecimal.ONE);
            productService.updateProduct(1, update);

            verify(productRepository).save(existing);
        }

        @Test
        @DisplayName("throws ProductNotFoundException when product does not exist")
        void throws_not_found_on_update() {
            authAs(7L, "SELLER");
            when(productRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(99, new Product()))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    // -------------------------------------------------------
    // deleteProduct — ownership guard
    // -------------------------------------------------------

    @Nested
    @DisplayName("deleteProduct — ownership guard")
    class DeleteOwnership {

        @Test
        @DisplayName("SELLER can delete their own product")
        void seller_can_delete_own_product() {
            authAs(5L, "SELLER");
            Product existing = productOwnedBy(3, "5");
            when(productRepository.findById(3)).thenReturn(Optional.of(existing));

            productService.deleteProduct(3);

            verify(productRepository).deleteById(3);
        }

        @Test
        @DisplayName("SELLER cannot delete another SELLER's product")
        void seller_cannot_delete_other_product() {
            authAs(5L, "SELLER");
            Product existing = productOwnedBy(3, "99");
            when(productRepository.findById(3)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> productService.deleteProduct(3))
                    .isInstanceOf(ProductForbiddenException.class);
        }

        @Test
        @DisplayName("ADMIN can delete any product")
        void admin_can_delete_any_product() {
            authAs(1L, "ADMIN");
            Product existing = productOwnedBy(3, "99");
            when(productRepository.findById(3)).thenReturn(Optional.of(existing));

            productService.deleteProduct(3);

            verify(productRepository).deleteById(3);
        }
    }
}
