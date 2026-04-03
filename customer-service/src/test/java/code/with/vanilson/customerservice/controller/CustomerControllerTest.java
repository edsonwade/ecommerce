package code.with.vanilson.customerservice.controller;

import code.with.vanilson.customerservice.*;
import code.with.vanilson.customerservice.exception.CustomerNotFoundException;
import code.with.vanilson.customerservice.exception.EmailAlreadyExistsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for CustomerController using Spring MockMvc.
 * <p>
 * Validates HTTP status codes, JSON content, validation errors, and service-layer error propagation.
 * Uses @WebMvcTest — does not start full Spring context.
 * </p>
 */
@WebMvcTest(CustomerController.class)
@DisplayName("CustomerController MockMvc Tests")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    @Autowired
    private ObjectMapper objectMapper;

    private Address address;
    private CustomerResponse customerResponse;
    private CustomerRequest validRequest;

    @BeforeEach
    void setUp() {
        address = new Address("Main St", "42", "12345", "US", "New York");
        customerResponse = new CustomerResponse(
                "cust-001", "John", "Doe", "john.doe@example.com", address);
        validRequest = new CustomerRequest(
                null, "John", "Doe", "john.doe@example.com", address);
    }

    // =============================================================
    // GET /api/v1/customers
    // =============================================================
    @Nested
    @DisplayName("GET /api/v1/customers")
    class GetAllCustomers {

        @Test
        @DisplayName("given_customers_exist_when_getAll_then_return_200_with_list")
        void given_customers_exist_when_getAll_then_return_200_with_list() throws Exception {
            when(customerService.findAllCustomers()).thenReturn(List.of(customerResponse));

            mockMvc.perform(get("/api/v1/customers"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].customerId", is("cust-001")))
                    .andExpect(jsonPath("$[0].email", is("john.doe@example.com")));

            verify(customerService).findAllCustomers();
        }

        @Test
        @DisplayName("given_no_customers_when_getAll_then_return_200_with_empty_list")
        void given_no_customers_when_getAll_then_return_200_with_empty_list() throws Exception {
            when(customerService.findAllCustomers()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // =============================================================
    // GET /api/v1/customers/{id}
    // =============================================================
    @Nested
    @DisplayName("GET /api/v1/customers/{id}")
    class GetCustomerById {

        @Test
        @DisplayName("given_valid_id_when_getById_then_return_200")
        void given_valid_id_when_getById_then_return_200() throws Exception {
            when(customerService.getCustomerById("cust-001")).thenReturn(customerResponse);

            mockMvc.perform(get("/api/v1/customers/{id}", "cust-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is("cust-001")))
                    .andExpect(jsonPath("$.firstname", is("John")))
                    .andExpect(jsonPath("$.lastname", is("Doe")))
                    .andExpect(jsonPath("$.email", is("john.doe@example.com")));

            verify(customerService).getCustomerById("cust-001");
        }

        @Test
        @DisplayName("given_invalid_id_when_getById_then_return_404")
        void given_invalid_id_when_getById_then_return_404() throws Exception {
            when(customerService.getCustomerById("invalid-id"))
                    .thenThrow(new CustomerNotFoundException("Not found", "customer.not.found.by.id"));

            mockMvc.perform(get("/api/v1/customers/{id}", "invalid-id"))
                    .andExpect(status().isNotFound());
        }
    }

    // =============================================================
    // GET /api/v1/customers/by-email
    // =============================================================
    @Nested
    @DisplayName("GET /api/v1/customers/by-email")
    class GetCustomerByEmail {

        @Test
        @DisplayName("given_valid_email_when_getByEmail_then_return_200")
        void given_valid_email_when_getByEmail_then_return_200() throws Exception {
            when(customerService.findByEmail("john.doe@example.com")).thenReturn(customerResponse);

            mockMvc.perform(get("/api/v1/customers/by-email")
                            .param("address", "john.doe@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email", is("john.doe@example.com")));
        }

        @Test
        @DisplayName("given_unknown_email_when_getByEmail_then_return_404")
        void given_unknown_email_when_getByEmail_then_return_404() throws Exception {
            when(customerService.findByEmail("unknown@example.com"))
                    .thenThrow(new CustomerNotFoundException("Not found", "customer.not.found.by.email"));

            mockMvc.perform(get("/api/v1/customers/by-email")
                            .param("address", "unknown@example.com"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("given_missing_address_param_when_getByEmail_then_return_400")
        void given_missing_address_param_when_getByEmail_then_return_400() throws Exception {
            mockMvc.perform(get("/api/v1/customers/by-email"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =============================================================
    // POST /api/v1/customers
    // =============================================================
    @Nested
    @DisplayName("POST /api/v1/customers")
    class CreateCustomer {

        @Test
        @DisplayName("given_valid_request_when_create_then_return_201")
        void given_valid_request_when_create_then_return_201() throws Exception {
            when(customerService.createCustomer(any(CustomerRequest.class))).thenReturn("cust-001");

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().string("cust-001"));

            verify(customerService).createCustomer(any(CustomerRequest.class));
        }

        @Test
        @DisplayName("given_duplicate_email_when_create_then_return_409")
        void given_duplicate_email_when_create_then_return_409() throws Exception {
            when(customerService.createCustomer(any(CustomerRequest.class)))
                    .thenThrow(new EmailAlreadyExistsException("Exists", "customer.email.already.exists"));

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("given_missing_firstname_when_create_then_return_400")
        void given_missing_firstname_when_create_then_return_400() throws Exception {
            CustomerRequest invalidRequest = new CustomerRequest(
                    null, null, "Doe", "john@example.com", null);

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("given_invalid_email_when_create_then_return_400")
        void given_invalid_email_when_create_then_return_400() throws Exception {
            CustomerRequest invalidRequest = new CustomerRequest(
                    null, "John", "Doe", "not-an-email", null);

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("given_empty_email_when_create_then_return_400")
        void given_empty_email_when_create_then_return_400() throws Exception {
            CustomerRequest invalidRequest = new CustomerRequest(
                    null, "John", "Doe", "", null);

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    // =============================================================
    // PUT /api/v1/customers/{id}
    // =============================================================
    @Nested
    @DisplayName("PUT /api/v1/customers/{id}")
    class UpdateCustomer {

        @Test
        @DisplayName("given_valid_update_when_update_then_return_200")
        void given_valid_update_when_update_then_return_200() throws Exception {
            when(customerService.updateCustomer(eq("cust-001"), any(CustomerRequest.class)))
                    .thenReturn(customerResponse);

            mockMvc.perform(put("/api/v1/customers/{id}", "cust-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is("cust-001")));
        }

        @Test
        @DisplayName("given_nonexistent_id_when_update_then_return_404")
        void given_nonexistent_id_when_update_then_return_404() throws Exception {
            when(customerService.updateCustomer(eq("invalid-id"), any(CustomerRequest.class)))
                    .thenThrow(new CustomerNotFoundException("Not found", "customer.not.found.by.id"));

            mockMvc.perform(put("/api/v1/customers/{id}", "invalid-id")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }
    }

    // =============================================================
    // DELETE /api/v1/customers/{id}
    // =============================================================
    @Nested
    @DisplayName("DELETE /api/v1/customers/{id}")
    class DeleteCustomer {

        @Test
        @DisplayName("given_valid_id_when_delete_then_return_204")
        void given_valid_id_when_delete_then_return_204() throws Exception {
            doNothing().when(customerService).deleteCustomer("cust-001");

            mockMvc.perform(delete("/api/v1/customers/{id}", "cust-001"))
                    .andExpect(status().isNoContent());

            verify(customerService).deleteCustomer("cust-001");
        }

        @Test
        @DisplayName("given_nonexistent_id_when_delete_then_return_404")
        void given_nonexistent_id_when_delete_then_return_404() throws Exception {
            doThrow(new CustomerNotFoundException("Not found", "customer.not.found.by.id"))
                    .when(customerService).deleteCustomer("invalid-id");

            mockMvc.perform(delete("/api/v1/customers/{id}", "invalid-id"))
                    .andExpect(status().isNotFound());
        }
    }
}
