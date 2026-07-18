package code.with.vanilson.productservice.review;

import code.with.vanilson.productservice.config.ProductSecurityConfig;
import code.with.vanilson.productservice.exception.ProductConflictException;
import code.with.vanilson.productservice.exception.ProductForbiddenException;
import code.with.vanilson.productservice.exception.ReviewVerificationException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReviewControllerTest — web-layer slice ({@code @WebMvcTest}) + security contract for the F7
 * review endpoints. The real {@link ProductSecurityConfig} is imported so the test proves the
 * intended access rules on the nested {@code /api/v1/products/**} paths: GET is public, POST/DELETE
 * require authentication. Service is mocked — no DB, no Feign.
 *
 * @author vamuhong
 * @version 1.0
 */
@WebMvcTest(ReviewController.class)
@Import(ProductSecurityConfig.class)
@DisplayName("ReviewController — Slice + Security Tests")
class ReviewControllerTest {

    @Autowired WebApplicationContext context;

    @MockBean ReviewService                  reviewService;
    @MockBean JwtAuthenticationFilter         jwtAuthenticationFilter;
    @MockBean TenantHibernateFilterActivator  tenantHibernateFilterActivator;

    private MockMvc mockMvc;

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

    private static ReviewResponse sampleResponse() {
        return new ReviewResponse(1L, 5, 42L, 5, "Great", LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /products/{id}/reviews (public)")
    class PublicList {

        @Test
        @DisplayName("returns 200 with a page — no authentication required")
        void publicListReturns200() throws Exception {
            when(reviewService.getReviews(anyInt(), any()))
                    .thenReturn(new PageImpl<>(List.of(sampleResponse())));

            mockMvc.perform(get("/api/v1/products/5/reviews").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].rating").value(5));
        }
    }

    @Nested
    @DisplayName("POST /products/{id}/reviews (authenticated)")
    class CreateReview {

        @Test
        @DisplayName("unauthenticated → 401")
        void unauthenticatedRejected() throws Exception {
            mockMvc.perform(post("/api/v1/products/5/reviews")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":5,\"comment\":\"ok\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("authenticated + valid body → 201")
        void authenticatedCreates() throws Exception {
            when(reviewService.createReview(anyInt(), any())).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/products/5/reviews")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":5,\"comment\":\"Great\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rating").value(5));
        }

        @Test
        @WithMockUser
        @DisplayName("rating out of range → 400 (bean validation)")
        void invalidRatingRejected() throws Exception {
            mockMvc.perform(post("/api/v1/products/5/reviews")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":6}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("service says not purchased → 403")
        void notPurchasedForbidden() throws Exception {
            when(reviewService.createReview(anyInt(), any()))
                    .thenThrow(new ProductForbiddenException("review.not.purchased", "review.not.purchased"));

            mockMvc.perform(post("/api/v1/products/5/reviews")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":4}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("service says duplicate → 409")
        void duplicateConflict() throws Exception {
            when(reviewService.createReview(anyInt(), any()))
                    .thenThrow(new ProductConflictException("review.already.exists", "review.already.exists"));

            mockMvc.perform(post("/api/v1/products/5/reviews")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":4}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser
        @DisplayName("verification unavailable → 503")
        void verificationUnavailable() throws Exception {
            when(reviewService.createReview(anyInt(), any()))
                    .thenThrow(new ReviewVerificationException(
                            "review.verification.unavailable", "review.verification.unavailable"));

            mockMvc.perform(post("/api/v1/products/5/reviews")
                            .header(TENANT_HDR, TENANT_VAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":4}"))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    @Nested
    @DisplayName("DELETE /products/reviews/{id} (authenticated)")
    class DeleteReview {

        @Test
        @DisplayName("unauthenticated → 401")
        void unauthenticatedRejected() throws Exception {
            mockMvc.perform(delete("/api/v1/products/reviews/7").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("authenticated → 204")
        void authenticatedDeletes() throws Exception {
            mockMvc.perform(delete("/api/v1/products/reviews/7").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser
        @DisplayName("service says forbidden → 403")
        void forbiddenDelete() throws Exception {
            doThrow(new ProductForbiddenException("review.delete.forbidden", "review.delete.forbidden"))
                    .when(reviewService).deleteReview(anyLong());

            mockMvc.perform(delete("/api/v1/products/reviews/7").header(TENANT_HDR, TENANT_VAL))
                    .andExpect(status().isForbidden());
        }
    }
}
