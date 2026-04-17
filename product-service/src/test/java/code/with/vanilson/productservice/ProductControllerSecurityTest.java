package code.with.vanilson.productservice;

import code.with.vanilson.productservice.config.ProductSecurityConfig;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(ProductSecurityConfig.class)
@DisplayName("ProductController — Security Tests")
class ProductControllerSecurityTest {

    @Autowired WebApplicationContext      context;

    @MockBean ProductService                 productService;
    @MockBean JwtAuthenticationFilter        jwtAuthenticationFilter;
    @MockBean TenantHibernateFilterActivator tenantHibernateFilterActivator;

    private MockMvc mockMvc;

    private static final String BASE       = "/api/v1/products";
    private static final String TENANT_HDR = "X-Tenant-ID";
    private static final String TENANT_VAL = "test-tenant-123";

    @BeforeEach
    void setUp() throws Exception {
        // Build MockMvc with default X-Tenant-ID header so TenantInterceptor is satisfied
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(get("/").header(TENANT_HDR, TENANT_VAL))
                .build();

        // JWT filter pass-through (no real token validation in tests)
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(
                    inv.<ServletRequest>getArgument(0),
                    inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    // -------------------------------------------------------
    // Public GET endpoints
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET endpoints (public)")
    class PublicGet {

        @Test
        @DisplayName("GET /products is public — no auth required")
        void list_products_is_public() throws Exception {
            when(productService.getAllProducts(any())).thenReturn(org.springframework.data.domain.Page.empty());

            mockMvc.perform(get(BASE).header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /products/{id} is public — no auth required")
        void get_by_id_is_public() throws Exception {
            when(productService.getProductById(anyInt()))
                    .thenReturn(java.util.Optional.of(new ProductResponse(
                            1, "Widget", "Desc", 5.0, BigDecimal.ONE, 1, "Cat", "CatDesc", "system")));

            mockMvc.perform(get(BASE + "/1").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // POST /create
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /create — SELLER or ADMIN only")
    class CreateProduct {

        private static final String BODY =
                "{\"name\":\"Widget\",\"description\":\"A widget\",\"availableQuantity\":10.0,\"price\":9.99," +
                "\"category\":{\"id\":1},\"createdBy\":null}";

        @Test
        @DisplayName("401 Unauthorized when no authentication")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(post(BASE + "/create")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_create() throws Exception {
            mockMvc.perform(post(BASE + "/create")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "SELLER")
        @DisplayName("201 Created for SELLER role")
        void seller_can_create() throws Exception {
            when(productService.createProduct(any()))
                    .thenReturn(new ProductRequest(1, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99), 1));

            mockMvc.perform(post(BASE + "/create")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("201 Created for ADMIN role")
        void admin_can_create() throws Exception {
            when(productService.createProduct(any()))
                    .thenReturn(new ProductRequest(1, "Widget", "A widget", 10.0, BigDecimal.valueOf(9.99), 1));

            mockMvc.perform(post(BASE + "/create")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "SELLER")
        @DisplayName("403 when service throws ProductForbiddenException (non-owner)")
        void seller_forbidden_on_non_owner_product() throws Exception {
            doThrow(new ProductForbiddenException("product.access.denied", "product.access.denied"))
                    .when(productService).createProduct(any());

            mockMvc.perform(post(BASE + "/create")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());
        }
    }

    // -------------------------------------------------------
    // PUT /update/{id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("PUT /update/{id} — SELLER or ADMIN only")
    class UpdateProduct {

        private static final String BODY =
                "{\"name\":\"Updated\",\"description\":\"Desc\",\"availableQuantity\":5.0,\"price\":1.0}";

        @Test
        @DisplayName("401 Unauthorized when no authentication")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(put(BASE + "/update/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_update() throws Exception {
            mockMvc.perform(put(BASE + "/update/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "SELLER")
        @DisplayName("200 OK when SELLER owns the product")
        void seller_can_update_own_product() throws Exception {
            when(productService.updateProduct(anyInt(), any()))
                    .thenReturn(new ProductRequest(1, "Updated", "Desc", 5.0, BigDecimal.ONE, 1));

            mockMvc.perform(put(BASE + "/update/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "SELLER")
        @DisplayName("403 when SELLER is not the owner (service throws)")
        void seller_cannot_update_non_owner() throws Exception {
            doThrow(new ProductForbiddenException("product.access.denied", "product.access.denied"))
                    .when(productService).updateProduct(anyInt(), any());

            mockMvc.perform(put(BASE + "/update/1")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());
        }
    }

    // -------------------------------------------------------
    // DELETE /delete/{id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("DELETE /delete/{id} — SELLER or ADMIN only")
    class DeleteProduct {

        @Test
        @DisplayName("401 Unauthorized when no authentication")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(delete(BASE + "/delete/1")
                            .header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("403 Forbidden for USER role")
        void user_cannot_delete() throws Exception {
            mockMvc.perform(delete(BASE + "/delete/1")
                            .header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "SELLER")
        @DisplayName("204 No Content when SELLER owns the product")
        void seller_can_delete_own_product() throws Exception {
            mockMvc.perform(delete(BASE + "/delete/1")
                            .header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "SELLER")
        @DisplayName("403 Forbidden when SELLER tries to delete another owner's product")
        void seller_cannot_delete_other_product() throws Exception {
            doThrow(new ProductForbiddenException("product.access.denied", "product.access.denied"))
                    .when(productService).deleteProduct(anyInt());

            mockMvc.perform(delete(BASE + "/delete/2")
                            .header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isForbidden());
        }
    }

    // -------------------------------------------------------
    // POST /purchase — authenticated only
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /purchase — authenticated only")
    class PurchaseProducts {

        @Test
        @DisplayName("401 Unauthorized when no authentication")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(post(BASE + "/purchase")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[]"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("200 OK for any authenticated user (USER role)")
        void authenticated_user_can_purchase() throws Exception {
            when(productService.purchaseProducts(any())).thenReturn(List.of());

            mockMvc.perform(post(BASE + "/purchase")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[]"))
                    .andExpect(status().isOk());
        }
    }
}
