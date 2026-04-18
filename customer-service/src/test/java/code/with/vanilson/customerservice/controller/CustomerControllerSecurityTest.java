package code.with.vanilson.customerservice.controller;

import code.with.vanilson.customerservice.CustomerController;
import code.with.vanilson.customerservice.CustomerResponse;
import code.with.vanilson.customerservice.CustomerService;
import code.with.vanilson.customerservice.config.CustomerSecurityConfig;
import code.with.vanilson.tenantcontext.security.JwtAuthenticationFilter;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import(CustomerSecurityConfig.class)
@DisplayName("CustomerController — Security Tests")
class CustomerControllerSecurityTest {

    @Autowired
    WebApplicationContext context;

    @MockBean CustomerService             customerService;
    @MockBean JwtAuthenticationFilter     jwtAuthenticationFilter;

    private MockMvc mockMvc;

    private static final String BASE = "/api/v1/customers";

    private static final String CREATE_BODY =
            "{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@example.com\"," +
            "\"address\":{\"street\":\"Main St\",\"houseNumber\":\"1\",\"zipCode\":\"12345\"," +
            "\"city\":\"Testville\",\"country\":\"Testland\"}}";

    private static final String UPDATE_BODY = CREATE_BODY;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private static UsernamePasswordAuthenticationToken authAs(long userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                new SecurityPrincipal("user@test.com", userId, "t1", role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private CustomerResponse sampleCustomer(String id) {
        return new CustomerResponse(id, "John", "Doe", "john@example.com", null);
    }

    // -------------------------------------------------------
    // GET /customers — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /customers — list all (ADMIN only)")
    class ListAll {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER lists customers")
        void user_cannot_list_customers() throws Exception {
            mockMvc.perform(get(BASE).with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("403 when SELLER lists customers")
        void seller_cannot_list_customers() throws Exception {
            mockMvc.perform(get(BASE).with(authentication(authAs(5L, "SELLER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN lists customers")
        void admin_lists_customers() throws Exception {
            when(customerService.findAllCustomers()).thenReturn(List.of(sampleCustomer("c-1")));
            mockMvc.perform(get(BASE).with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // GET /customers/{id} — owner or ADMIN
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /customers/{id} — owner or ADMIN")
    class GetById {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/42")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 when USER accesses own profile")
        void user_accesses_own_profile() throws Exception {
            when(customerService.getCustomerById("42")).thenReturn(sampleCustomer("42"));
            mockMvc.perform(get(BASE + "/42").with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 when USER accesses another customer's profile")
        void user_cannot_access_foreign_profile() throws Exception {
            mockMvc.perform(get(BASE + "/99").with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN accesses any profile")
        void admin_accesses_any_profile() throws Exception {
            when(customerService.getCustomerById("99")).thenReturn(sampleCustomer("99"));
            mockMvc.perform(get(BASE + "/99").with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // GET /customers/by-email — ADMIN only
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /customers/by-email — ADMIN only")
    class GetByEmail {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(get(BASE + "/by-email").param("address", "john@example.com"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when USER queries by email")
        void user_cannot_query_by_email() throws Exception {
            mockMvc.perform(get(BASE + "/by-email").param("address", "john@example.com")
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN queries by email")
        void admin_can_query_by_email() throws Exception {
            when(customerService.findByEmail(anyString())).thenReturn(sampleCustomer("42"));
            mockMvc.perform(get(BASE + "/by-email").param("address", "john@example.com")
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // POST /customers — authenticated
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /customers — authenticated")
    class CreateCustomer {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("201 when USER creates customer")
        void user_can_create_customer() throws Exception {
            when(customerService.createCustomer(any())).thenReturn("c-001");
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("201 when ADMIN creates customer")
        void admin_can_create_customer() throws Exception {
            when(customerService.createCustomer(any())).thenReturn("c-001");
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isCreated());
        }
    }

    // -------------------------------------------------------
    // PUT /customers/{id} — owner or ADMIN
    // -------------------------------------------------------

    @Nested
    @DisplayName("PUT /customers/{id} — owner or ADMIN")
    class UpdateCustomer {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(put(BASE + "/42")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("200 when USER updates own profile")
        void user_can_update_own_profile() throws Exception {
            when(customerService.updateCustomer(anyString(), any())).thenReturn(sampleCustomer("42"));
            mockMvc.perform(put(BASE + "/42")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 when USER updates another's profile")
        void user_cannot_update_foreign_profile() throws Exception {
            mockMvc.perform(put(BASE + "/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY)
                            .with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("200 when ADMIN updates any profile")
        void admin_can_update_any_profile() throws Exception {
            when(customerService.updateCustomer(anyString(), any())).thenReturn(sampleCustomer("99"));
            mockMvc.perform(put(BASE + "/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY)
                            .with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------
    // DELETE /customers/{id} — owner or ADMIN
    // -------------------------------------------------------

    @Nested
    @DisplayName("DELETE /customers/{id} — owner or ADMIN")
    class DeleteCustomer {

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated_gets_401() throws Exception {
            mockMvc.perform(delete(BASE + "/42")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("204 when USER deletes own profile")
        void user_can_delete_own_profile() throws Exception {
            mockMvc.perform(delete(BASE + "/42").with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("403 when USER deletes another's profile")
        void user_cannot_delete_foreign_profile() throws Exception {
            mockMvc.perform(delete(BASE + "/99").with(authentication(authAs(42L, "USER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("204 when ADMIN deletes any profile")
        void admin_can_delete_any_profile() throws Exception {
            mockMvc.perform(delete(BASE + "/99").with(authentication(authAs(1L, "ADMIN"))))
                    .andExpect(status().isNoContent());
        }
    }
}
