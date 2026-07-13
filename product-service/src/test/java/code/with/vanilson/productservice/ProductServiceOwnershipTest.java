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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import code.with.vanilson.productservice.category.Category;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService — Ownership Tests")
class ProductServiceOwnershipTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper     productMapper;
    @Mock private MessageSource     messageSource;
    @Mock private code.with.vanilson.tenantcontext.TenantHibernateFilterActivator filterActivator;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // Shared default: echo the message key. lenient() — a few tests (e.g. catalogScopeKey)
        // exercise paths that resolve no messages, and strict stubbing would flag this as unused.
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(long userId, String role) {
        authAs(userId, role, null);
    }

    private void authAs(long userId, String role, String sellerStatus) {
        var principal = new SecurityPrincipal("u@x.com", userId, "t1", role, sellerStatus);
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

    // -------------------------------------------------------
    // Seller approval write-guard (Fase 2)
    // -------------------------------------------------------

    @Nested
    @DisplayName("seller approval write-guard")
    class SellerApprovalWriteGuard {

        private Product newProduct() {
            return productOwnedBy(0, null);
        }

        @Test
        @DisplayName("PENDING_APPROVAL seller cannot create — 403 seller.not.approved")
        void pending_seller_cannot_create() {
            authAs(7L, "SELLER", "PENDING_APPROVAL");

            assertThatThrownBy(() -> productService.createProduct(newProduct()))
                    .isInstanceOf(ProductForbiddenException.class)
                    .satisfies(ex -> assertThat(
                            ((ProductForbiddenException) ex).getMessageKey())
                            .isEqualTo("seller.not.approved"));

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("SUSPENDED seller cannot create — 403 seller.suspended")
        void suspended_seller_cannot_create() {
            authAs(7L, "SELLER", "SUSPENDED");

            assertThatThrownBy(() -> productService.createProduct(newProduct()))
                    .isInstanceOf(ProductForbiddenException.class)
                    .satisfies(ex -> assertThat(
                            ((ProductForbiddenException) ex).getMessageKey())
                            .isEqualTo("seller.suspended"));

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("APPROVED seller creates normally")
        void approved_seller_can_create() {
            authAs(7L, "SELLER", "APPROVED");
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductRequest(any())).thenReturn(
                    new ProductRequest(0, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99), 1));

            productService.createProduct(newProduct());

            verify(productRepository).save(any());
        }

        @Test
        @DisplayName("seller with old token (null claim) still creates — grandfathered compat")
        void null_claim_seller_can_create() {
            authAs(7L, "SELLER", null);
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductRequest(any())).thenReturn(
                    new ProductRequest(0, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99), 1));

            productService.createProduct(newProduct());

            verify(productRepository).save(any());
        }

        @Test
        @DisplayName("ADMIN is never gated by seller status")
        void admin_is_never_gated() {
            authAs(1L, "ADMIN", null);
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductRequest(any())).thenReturn(
                    new ProductRequest(0, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99), 1));

            productService.createProduct(newProduct());

            verify(productRepository).save(any());
        }

        @Test
        @DisplayName("PENDING_APPROVAL seller cannot update even their OWN product")
        void pending_seller_cannot_update_own_product() {
            authAs(7L, "SELLER", "PENDING_APPROVAL");

            assertThatThrownBy(() -> productService.updateProduct(1,
                    new Product(1, "Updated", "Desc", 5.0, BigDecimal.ONE)))
                    .isInstanceOf(ProductForbiddenException.class)
                    .satisfies(ex -> assertThat(
                            ((ProductForbiddenException) ex).getMessageKey())
                            .isEqualTo("seller.not.approved"));

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("SUSPENDED seller cannot delete even their OWN product")
        void suspended_seller_cannot_delete_own_product() {
            authAs(5L, "SELLER", "SUSPENDED");

            assertThatThrownBy(() -> productService.deleteProduct(3))
                    .isInstanceOf(ProductForbiddenException.class)
                    .satisfies(ex -> assertThat(
                            ((ProductForbiddenException) ex).getMessageKey())
                            .isEqualTo("seller.suspended"));

            verify(productRepository, never()).deleteById(any());
        }
    }

    // -------------------------------------------------------
    // Catalogue scoping — a SELLER browses only their own products
    // -------------------------------------------------------

    @Nested
    @DisplayName("catalogue scoping — SELLER sees only own products")
    class CatalogScoping {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("getAllProducts: SELLER is scoped to their own products (findByCreatedBy)")
        void seller_catalog_scoped_to_own() {
            authAs(7L, "SELLER");
            when(productRepository.findByCreatedBy("7", pageable)).thenReturn(Page.empty(pageable));

            productService.getAllProducts(pageable);

            verify(productRepository).findByCreatedBy("7", pageable);
            verify(productRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("getAllProducts: guest (no principal) sees the ACTIVE catalogue (findByStatus) — Fase 3")
        void guest_sees_full_catalog() {
            when(productRepository.findByStatus(ProductStatus.ACTIVE, pageable)).thenReturn(Page.empty(pageable));

            productService.getAllProducts(pageable);

            verify(productRepository).findByStatus(ProductStatus.ACTIVE, pageable);
            verify(productRepository, never()).findByCreatedBy(anyString(), any(Pageable.class));
        }

        @Test
        @DisplayName("getAllProducts: customer (USER) sees the ACTIVE catalogue — Fase 3")
        void customer_sees_full_catalog() {
            authAs(3L, "USER");
            when(productRepository.findByStatus(ProductStatus.ACTIVE, pageable)).thenReturn(Page.empty(pageable));

            productService.getAllProducts(pageable);

            verify(productRepository).findByStatus(ProductStatus.ACTIVE, pageable);
            verify(productRepository, never()).findByCreatedBy(anyString(), any(Pageable.class));
        }

        @Test
        @DisplayName("getAllProducts: ADMIN's public list is also ACTIVE-only (full view = /products/admin) — Fase 3")
        void admin_sees_full_catalog() {
            authAs(1L, "ADMIN");
            when(productRepository.findByStatus(ProductStatus.ACTIVE, pageable)).thenReturn(Page.empty(pageable));

            productService.getAllProducts(pageable);

            verify(productRepository).findByStatus(ProductStatus.ACTIVE, pageable);
            verify(productRepository, never()).findByCreatedBy(anyString(), any(Pageable.class));
        }

        @Test
        @DisplayName("catalogScopeKey: 'seller:<id>' for SELLER, 'all' for everyone else")
        void catalog_scope_key_discriminates_seller() {
            authAs(7L, "SELLER");
            assertThat(productService.catalogScopeKey()).isEqualTo("seller:7");

            SecurityContextHolder.clearContext();
            assertThat(productService.catalogScopeKey()).isEqualTo("all");
        }
    }

    // -------------------------------------------------------
    // Fase 3 — admin status management (Task 3.4)
    // -------------------------------------------------------

    @Nested
    @DisplayName("admin status management (Fase 3 Task 3.4)")
    class AdminStatusManagement {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("updateProductStatus: sets SUSPENDED, saves, and returns the mapped response")
        void update_status_suspends_and_saves() {
            authAs(1L, "ADMIN");
            Product target = productOwnedBy(5, "7");
            when(productRepository.findById(5)).thenReturn(Optional.of(target));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.fromProduct(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                return new ProductResponse(p.getId(), p.getName(), p.getDescription(),
                        p.getAvailableQuantity(), p.getPrice(), 1, "Electronics", "Devices",
                        p.getCreatedBy(), null, p.getStatus());
            });

            ProductResponse response = productService.updateProductStatus(5, ProductStatus.SUSPENDED);

            assertThat(response.status()).isEqualTo(ProductStatus.SUSPENDED);
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ProductStatus.SUSPENDED);
        }

        @Test
        @DisplayName("updateProductStatus: reactivation flips SUSPENDED back to ACTIVE")
        void update_status_reactivates() {
            authAs(1L, "ADMIN");
            Product target = productOwnedBy(5, "7");
            target.setStatus(ProductStatus.SUSPENDED);
            when(productRepository.findById(5)).thenReturn(Optional.of(target));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.fromProduct(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                return new ProductResponse(p.getId(), p.getName(), p.getDescription(),
                        p.getAvailableQuantity(), p.getPrice(), 1, "Electronics", "Devices",
                        p.getCreatedBy(), null, p.getStatus());
            });

            ProductResponse response = productService.updateProductStatus(5, ProductStatus.ACTIVE);

            assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("updateProductStatus: unknown product → ProductNotFoundException")
        void update_status_missing_product_404() {
            authAs(1L, "ADMIN");
            when(productRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProductStatus(99, ProductStatus.SUSPENDED))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("getAllProductsForAdmin: unfiltered findAll — suspended products included")
        void admin_list_is_unfiltered() {
            authAs(1L, "ADMIN");
            when(productRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

            productService.getAllProductsForAdmin(pageable);

            verify(productRepository).findAll(pageable);
            verify(productRepository, never()).findByStatus(any(ProductStatus.class), any(Pageable.class));
            verify(productRepository, never()).findByCreatedBy(anyString(), any(Pageable.class));
        }
    }

    // -------------------------------------------------------
    // Fase 3 — suspended product detail visibility (Task 3.2)
    // -------------------------------------------------------

    @Nested
    @DisplayName("suspended detail visibility — owner/ADMIN only (Fase 3)")
    class SuspendedDetailVisibility {

        private Product suspendedOwnedBy7() {
            Product p = productOwnedBy(5, "7");
            p.setStatus(ProductStatus.SUSPENDED);
            return p;
        }

        private ProductResponse suspendedResponse() {
            return new ProductResponse(5, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99),
                    1, "Electronics", "Devices", "7", null, ProductStatus.SUSPENDED);
        }

        @Test
        @DisplayName("anonymous caller: suspended detail → 404 (no existence leak)")
        void anonymous_gets_404_for_suspended() {
            when(productRepository.findById(5)).thenReturn(Optional.of(suspendedOwnedBy7()));

            assertThatThrownBy(() -> productService.getProductById(5))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("customer (USER, not owner): suspended detail → 404")
        void other_user_gets_404_for_suspended() {
            authAs(3L, "USER");
            when(productRepository.findById(5)).thenReturn(Optional.of(suspendedOwnedBy7()));

            assertThatThrownBy(() -> productService.getProductById(5))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("another SELLER (not owner): suspended detail → 404")
        void other_seller_gets_404_for_suspended() {
            authAs(8L, "SELLER", "APPROVED");
            when(productRepository.findById(5)).thenReturn(Optional.of(suspendedOwnedBy7()));

            assertThatThrownBy(() -> productService.getProductById(5))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("owner SELLER: keeps seeing their own suspended product")
        void owner_sees_own_suspended() {
            authAs(7L, "SELLER", "APPROVED");
            Product suspended = suspendedOwnedBy7();
            when(productRepository.findById(5)).thenReturn(Optional.of(suspended));
            when(productMapper.fromProduct(suspended)).thenReturn(suspendedResponse());

            ProductResponse response = productService.getProductById(5);

            assertThat(response.status()).isEqualTo(ProductStatus.SUSPENDED);
        }

        @Test
        @DisplayName("ADMIN: sees any suspended product")
        void admin_sees_suspended() {
            authAs(1L, "ADMIN");
            Product suspended = suspendedOwnedBy7();
            when(productRepository.findById(5)).thenReturn(Optional.of(suspended));
            when(productMapper.fromProduct(suspended)).thenReturn(suspendedResponse());

            ProductResponse response = productService.getProductById(5);

            assertThat(response.status()).isEqualTo(ProductStatus.SUSPENDED);
        }

        @Test
        @DisplayName("ACTIVE product: visible to everyone exactly as before (regression)")
        void active_product_unaffected() {
            Product active = productOwnedBy(6, "7");
            when(productRepository.findById(6)).thenReturn(Optional.of(active));
            when(productMapper.fromProduct(active)).thenReturn(new ProductResponse(
                    6, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99),
                    1, "Electronics", "Devices", "7", null, ProductStatus.ACTIVE));

            ProductResponse response = productService.getProductById(6);

            assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
        }
    }
}
