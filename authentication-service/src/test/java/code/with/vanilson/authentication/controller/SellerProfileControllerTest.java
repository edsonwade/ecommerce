package code.with.vanilson.authentication.controller;

import code.with.vanilson.authentication.application.SellerProfileResponse;
import code.with.vanilson.authentication.application.SellerProfileService;
import code.with.vanilson.authentication.config.JwtAuthFilter;
import code.with.vanilson.authentication.config.SecurityConfig;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import code.with.vanilson.authentication.presentation.SellerProfileController;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SellerProfileController — WebMvc slice tests.
 * <p>
 * Covers the "sold by" lookup used on order invoices, including the regression where a
 * non-numeric owner sentinel (seed products are owned by {@code "system"}) was passed to the
 * {@code @PathVariable Long id} and surfaced as a 500 instead of a 400.
 * </p>
 */
@WebMvcTest(SellerProfileController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("SellerProfileController - WebMvc slice tests")
class SellerProfileControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean SellerProfileService   service;
    @MockBean JwtService             jwtService;
    @MockBean TokenRepository        tokenRepository;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthFilter          jwtAuthFilter;

    private static final String BASE = "/api/v1/auth/sellers";

    private UserDetailsAdapter buyerPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        // Forward all requests past the JWT filter so Spring Security rules can run.
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(
                    inv.<ServletRequest>getArgument(0),
                    inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());

        buyerPrincipal = new UserDetailsAdapter(
                User.builder().id(5L).email("buyer@x.com")
                        .role(Role.USER).tenantId("default-tenant")
                        .firstname("Wayne").lastname("Edson")
                        .password("hashed").accountEnabled(true).build());
    }

    @Nested
    @DisplayName("GET /api/v1/auth/sellers/{id}")
    class GetSeller {

        @Test
        @DisplayName("200 with the seller business profile for an authenticated buyer")
        void returns_profile_for_numeric_id() throws Exception {
            SellerProfileResponse profile = new SellerProfileResponse(
                    7L, "Fabio Teixeira", "Fabio", "Teixeira", "fabio@qa.com",
                    "Wayne Corpoartions Lda", "1231234567",
                    "Luz Soriano 149", "Porto", "Portugal", "3456-099");
            when(service.getSellerProfile(eq(7L))).thenReturn(profile);

            mockMvc.perform(get(BASE + "/7").with(user(buyerPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(7)))
                    .andExpect(jsonPath("$.companyName", is("Wayne Corpoartions Lda")))
                    .andExpect(jsonPath("$.vatNumber", is("1231234567")));
        }

        @Test
        @DisplayName("400 (not 500) when the owner is the non-numeric 'system' sentinel")
        void returns_400_for_system_sentinel() throws Exception {
            // Regression: seed/catalog products are owned by "system"; the order-detail page
            // used to call GET /auth/sellers/system, which 500'd. It must now be a clean 400.
            mockMvc.perform(get(BASE + "/system").with(user(buyerPrincipal)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.bad.request")))
                    .andExpect(jsonPath("$.status", is(400)));
        }

        @Test
        @DisplayName("404 when the seller id does not exist")
        void returns_404_for_missing_seller() throws Exception {
            when(service.getSellerProfile(eq(99L)))
                    .thenThrow(new AuthUserNotFoundException("Seller 99 not found", "auth.user.not.found"));

            mockMvc.perform(get(BASE + "/99").with(user(buyerPrincipal)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("401 Unauthorized for an anonymous request")
        void anonymous_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/7"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
