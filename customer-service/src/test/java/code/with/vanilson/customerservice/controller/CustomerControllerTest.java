package code.with.vanilson.customerservice.controller;

import code.with.vanilson.customerservice.Address;
import code.with.vanilson.customerservice.CustomerController;
import code.with.vanilson.customerservice.CustomerRequest;
import code.with.vanilson.customerservice.CustomerResponse;
import code.with.vanilson.customerservice.CustomerService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CustomerControllerTest — Controller (Web Layer) Tests
 * <p>
 * Uses @WebMvcTest to load ONLY the web layer (no full Spring context).
 * CustomerService is mocked — business logic is not tested here.
 * <p>
 * Tests verify:
 * - HTTP status codes
 * - Request/response JSON structure
 * - Correct service method delegation
 * - Validation error responses (400)
 * - Error responses from exception handler (404, 409)
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@WebMvcTest(CustomerController.class)
@DisplayName("CustomerController — Web Layer Tests")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    private CustomerResponse response1;
    private CustomerResponse response2;
    private CustomerRequest validRequest;

    @BeforeEach
    void setUp() {
        var address = new Address("Main St", "42", "10001", "Porto Alegre", "RS");
        response1 = new CustomerResponse("cust-001", "Ana", "Silva", "ana@example.com", address);
        response2 = new CustomerResponse("cust-002", "Bruno", "Costa", "bruno@example.com", null);
        validRequest = new CustomerRequest(null, "Ana", "Silva", "ana@example.com", address);
    }

    // -------------------------------------------------------
    // GET /api/v1/customers
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/customers")
    class GetAll {

        @Test
        @DisplayName("should return 200 with list of customers")
        void shouldReturn200WithCustomerList() throws Exception {
            when(customerService.findAllCustomers()).thenReturn(List.of(response1, response2));

            mockMvc.perform(get("/api/v1/customers")
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].customerId", is("cust-001")))
                    .andExpect(jsonPath("$[0].email", is("ana@example.com")))
                    .andExpect(jsonPath("$[1].customerId", is("cust-002")));

            verify(customerService, times(1)).findAllCustomers();
        }

        @Test
        @DisplayName("should return 200 with empty array when no customers")
        void shouldReturn200WithEmptyArray() throws Exception {
            when(customerService.findAllCustomers()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // -------------------------------------------------------
    // GET /api/v1/customers/{id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/customers/{id}")
    class GetById {

        @Test
        @DisplayName("should return 200 with customer when found")
        void shouldReturn200WhenFound() throws Exception {
            when(customerService.getCustomerById("cust-001")).thenReturn(response1);

            mockMvc.perform(get("/api/v1/customers/cust-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is("cust-001")))
                    .andExpect(jsonPath("$.email", is("ana@example.com")))
                    .andExpect(jsonPath("$.firstname", is("Ana")));

            verify(customerService, times(1)).getCustomerById("cust-001");
        }

        @Test
        @DisplayName("should return 404 when customer not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(customerService.getCustomerById("ghost"))
                    .thenThrow(new CustomerNotFoundException(
                            "Customer with ID [ghost] not found.",
                            "customer.not.found.by.id"));

            mockMvc.perform(get("/api/v1/customers/ghost"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.errorCode", is("customer.not.found.by.id")));
        }
    }

    // -------------------------------------------------------
    // GET /api/v1/customers/by-email?address=...
    // -------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/customers/by-email")
    class GetByEmail {

        @Test
        @DisplayName("should return 200 when customer found by email")
        void shouldReturn200WhenFound() throws Exception {
            when(customerService.findByEmail("ana@example.com")).thenReturn(response1);

            mockMvc.perform(get("/api/v1/customers/by-email")
                            .param("address", "ana@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email", is("ana@example.com")));

            verify(customerService, times(1)).findByEmail("ana@example.com");
        }

        @Test
        @DisplayName("should return 404 when email not found")
        void shouldReturn404WhenEmailNotFound() throws Exception {
            when(customerService.findByEmail("ghost@example.com"))
                    .thenThrow(new CustomerNotFoundException(
                            "Customer with email [ghost@example.com] not found.",
                            "customer.not.found.by.email"));

            mockMvc.perform(get("/api/v1/customers/by-email")
                            .param("address", "ghost@example.com"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode", is("customer.not.found.by.email")));
        }
    }

    // -------------------------------------------------------
    // POST /api/v1/customers
    // -------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/customers")
    class Create {

        @Test
        @DisplayName("should return 201 with customer ID on successful creation")
        void shouldReturn201OnCreate() throws Exception {
            when(customerService.createCustomer(any(CustomerRequest.class))).thenReturn("cust-001");

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$", is("cust-001")));

            verify(customerService, times(1)).createCustomer(any(CustomerRequest.class));
        }

        @Test
        @DisplayName("should return 400 when required fields are missing")
        void shouldReturn400OnValidationError() throws Exception {
            // firstname is null — violates @NotNull
            CustomerRequest invalidRequest = new CustomerRequest(null, null, "Silva", "ana@example.com", null);

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 409 when email already registered")
        void shouldReturn409WhenEmailDuplicate() throws Exception {
            when(customerService.createCustomer(any()))
                    .thenThrow(new EmailAlreadyExistsException(
                            "A customer with email [ana@example.com] is already registered.",
                            "customer.email.already.exists"));

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode", is("customer.email.already.exists")));
        }

        @Test
        @DisplayName("should return 400 when email format is invalid")
        void shouldReturn400WhenEmailInvalid() throws Exception {
            CustomerRequest badEmail = new CustomerRequest(null, "Ana", "Silva", "not-an-email", null);

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badEmail)))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------
    // PUT /api/v1/customers/{id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/v1/customers/{id}")
    class Update {

        @Test
        @DisplayName("should return 200 with updated customer")
        void shouldReturn200OnUpdate() throws Exception {
            when(customerService.updateCustomer(eq("cust-001"), any(CustomerRequest.class)))
                    .thenReturn(response1);

            mockMvc.perform(put("/api/v1/customers/cust-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is("cust-001")));

            verify(customerService, times(1)).updateCustomer(eq("cust-001"), any());
        }

        @Test
        @DisplayName("should return 404 when customer not found on update")
        void shouldReturn404WhenNotFound() throws Exception {
            when(customerService.updateCustomer(eq("ghost"), any()))
                    .thenThrow(new CustomerNotFoundException(
                            "Customer with ID [ghost] not found.",
                            "customer.not.found.by.id"));

            mockMvc.perform(put("/api/v1/customers/ghost")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------
    // DELETE /api/v1/customers/{id}
    // -------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/customers/{id}")
    class Delete {

        @Test
        @DisplayName("should return 204 No Content on successful delete")
        void shouldReturn204OnDelete() throws Exception {
            doNothing().when(customerService).deleteCustomer("cust-001");

            mockMvc.perform(delete("/api/v1/customers/cust-001"))
                    .andExpect(status().isNoContent());

            verify(customerService, times(1)).deleteCustomer("cust-001");
        }

        @Test
        @DisplayName("should return 404 when customer not found on delete")
        void shouldReturn404WhenNotFound() throws Exception {
            doThrow(new CustomerNotFoundException(
                    "Customer with ID [ghost] not found.",
                    "customer.not.found.by.id"))
                    .when(customerService).deleteCustomer("ghost");

            mockMvc.perform(delete("/api/v1/customers/ghost"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode", is("customer.not.found.by.id")));
        }
    }
}
