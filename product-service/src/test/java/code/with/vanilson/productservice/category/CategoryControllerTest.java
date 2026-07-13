package code.with.vanilson.productservice.category;

import code.with.vanilson.productservice.config.ProductSecurityConfig;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductNotFoundException;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CategoryControllerTest — web-layer slice ({@code @WebMvcTest}) for the Fase 4 category
 * endpoints. Covers both the security contract (public GET, ADMIN-only writes) and the
 * status-code mapping of the service's success / 404 / 409 outcomes through the global
 * exception handler. Service is mocked — no database.
 */
@WebMvcTest(CategoryController.class)
@Import(ProductSecurityConfig.class)
@DisplayName("CategoryController — Slice + Security Tests")
class CategoryControllerTest {

    @Autowired WebApplicationContext context;

    @MockBean CategoryService                 categoryService;
    @MockBean JwtAuthenticationFilter         jwtAuthenticationFilter;
    @MockBean TenantHibernateFilterActivator  tenantHibernateFilterActivator;

    private MockMvc mockMvc;

    private static final String BASE       = "/api/v1/categories";
    private static final String TENANT_HDR = "X-Tenant-ID";
    private static final String TENANT_VAL = "test-tenant-123";

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(get("/").header(TENANT_HDR, TENANT_VAL))
                .build();

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private static String json(String name, String desc) {
        return "{\"name\":\"" + name + "\",\"description\":\"" + desc + "\"}";
    }

    // -------------------------------------------------------
    // Public read
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /categories (public)")
    class PublicList {

        @Test
        @DisplayName("anonymous → 200")
        void list_is_public() throws Exception {
            when(categoryService.getAllCategories()).thenReturn(List.of());

            mockMvc.perform(get(BASE).header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // Write security — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("Write endpoints — ADMIN only")
    class WriteSecurity {

        @Test
        @DisplayName("POST anonymous → 401")
        void post_anonymous_401() throws Exception {
            mockMvc.perform(post(BASE).header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("Cables", "d")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("POST USER → 403")
        void post_user_403() throws Exception {
            mockMvc.perform(post(BASE).header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("Cables", "d")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "SELLER")
        @DisplayName("POST SELLER → 403")
        void post_seller_403() throws Exception {
            mockMvc.perform(post(BASE).header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("Cables", "d")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("PUT USER → 403")
        void put_user_403() throws Exception {
            mockMvc.perform(put(BASE + "/5").header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("New", "d")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "SELLER")
        @DisplayName("DELETE SELLER → 403")
        void delete_seller_403() throws Exception {
            mockMvc.perform(delete(BASE + "/5").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isForbidden());
        }
    }

    // -------------------------------------------------------
    // ADMIN happy paths + service-outcome mapping
    // -------------------------------------------------------

    @Nested
    @DisplayName("ADMIN operations")
    class AdminOps {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("POST valid → 201")
        void post_admin_201() throws Exception {
            when(categoryService.createCategory(any()))
                    .thenReturn(new CategoryResponse(500, "Cables", "d"));

            mockMvc.perform(post(BASE).header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("Cables", "d")))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("POST blank name → 400 (bean validation)")
        void post_blank_name_400() throws Exception {
            mockMvc.perform(post(BASE).header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("", "d")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("POST duplicate name → 409")
        void post_duplicate_409() throws Exception {
            when(categoryService.createCategory(any())).thenThrow(
                    new ProductConflictException("dup", "category.name.exists"));

            mockMvc.perform(post(BASE).header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("Keyboards", "d")))
                    .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("PUT valid → 200")
        void put_admin_200() throws Exception {
            when(categoryService.updateCategory(eq(5), any()))
                    .thenReturn(new CategoryResponse(5, "New", "d"));

            mockMvc.perform(put(BASE + "/5").header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("New", "d")))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("PUT missing category → 404")
        void put_missing_404() throws Exception {
            when(categoryService.updateCategory(eq(99), any())).thenThrow(
                    new ProductNotFoundException("nope", "category.not.found"));

            mockMvc.perform(put(BASE + "/99").header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON).content(json("New", "d")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("DELETE unreferenced → 204")
        void delete_admin_204() throws Exception {
            doNothing().when(categoryService).deleteCategory(5);

            mockMvc.perform(delete(BASE + "/5").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("DELETE referenced-by-products → 409")
        void delete_referenced_409() throws Exception {
            doThrow(new ProductConflictException("has products", "category.delete.has.products"))
                    .when(categoryService).deleteCategory(anyInt());

            mockMvc.perform(delete(BASE + "/1").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isConflict());
        }
    }
}
