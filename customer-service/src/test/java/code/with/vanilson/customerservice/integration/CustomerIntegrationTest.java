//package code.with.vanilson.customerservice.integration;
//
//import code.with.vanilson.customerservice.Address;
//import code.with.vanilson.customerservice.Customer;
//import code.with.vanilson.customerservice.CustomerRepository;
//import code.with.vanilson.customerservice.CustomerRequest;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.hamcrest.Matchers.hasSize;
//import static org.hamcrest.Matchers.is;
//import static org.hamcrest.Matchers.notNullValue;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
//import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
///**
// * CustomerIntegrationTest — Integration Tests (Testcontainers MongoDB)
// * <p>
// * Spins up real MongoDB and Redis containers via AbstractIntegrationTest.
// * Verifies full request-response cycle through the web layer, service, and real DB.
// * <p>
// * Uses @ActiveProfiles("test") to load application-test.yml.
// * Each test method starts with a clean database (deleteAll in @BeforeEach).
// * </p>
// *
// * @author vamuhong
// * @version 3.0
// */
//@AutoConfigureMockMvc
//@DisplayName("CustomerController — Integration Tests (Testcontainers)")
//class CustomerIntegrationTest extends AbstractIntegrationTest {
//
//    @Autowired private MockMvc         mockMvc;
//    @Autowired private ObjectMapper    objectMapper;
//    @Autowired private CustomerRepository customerRepository;
//
//    private Address address;
//
//    @BeforeEach
//    void cleanDatabase() {
//        customerRepository.deleteAll();
//        address = new Address("Main St", "42", "10001", "Porto Alegre", "RS");
//    }
//
//    // -------------------------------------------------------
//    // POST + GET full flow
//    // -------------------------------------------------------
//
//    @Nested @DisplayName("Create and retrieve customer flow")
//    class CreateAndRetrieve {
//
//        @Test
//        @DisplayName("POST creates customer → GET retrieves it by ID")
//        void shouldCreateAndRetrieveById() throws Exception {
//            CustomerRequest request = new CustomerRequest(
//                    null, "Ana", "Silva", "ana@example.com", address);
//
//            // Create
//            String id = mockMvc.perform(post("/api/v1/customers")
//                            .contentType(MediaType.APPLICATION_JSON)
//                            .content(objectMapper.writeValueAsString(request)))
//                    .andExpect(status().isCreated())
//                    .andReturn().getResponse().getContentAsString()
//                    .replace("\"", ""); // strip quotes from plain string response
//
//            assertThat(id).isNotBlank();
//
//            // Retrieve by ID
//            mockMvc.perform(get("/api/v1/customers/" + id))
//                    .andDo(print())
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.customerId", is(id)))
//                    .andExpect(jsonPath("$.email",      is("ana@example.com")))
//                    .andExpect(jsonPath("$.firstname",  is("Ana")));
//        }
//
//        @Test
//        @DisplayName("POST creates customer → GET retrieves it by email")
//        void shouldCreateAndRetrieveByEmail() throws Exception {
//            customerRepository.save(Customer.builder()
//                    .customerId("cust-db-001")
//                    .firstname("Maria")
//                    .lastname("Oliveira")
//                    .email("maria@example.com")
//                    .address(address)
//                    .build());
//
//            mockMvc.perform(get("/api/v1/customers/by-email")
//                            .param("address", "maria@example.com"))
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.email", is("maria@example.com")));
//        }
//
//        @Test
//        @DisplayName("GET all customers returns all persisted customers")
//        void shouldReturnAllPersistedCustomers() throws Exception {
//            customerRepository.saveAll(java.util.List.of(
//                    Customer.builder().customerId("c1").firstname("A").lastname("B")
//                            .email("a@example.com").build(),
//                    Customer.builder().customerId("c2").firstname("C").lastname("D")
//                            .email("c@example.com").build()
//            ));
//
//            mockMvc.perform(get("/api/v1/customers"))
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$", hasSize(2)));
//        }
//    }
//
//    // -------------------------------------------------------
//    // Duplicate email
//    // -------------------------------------------------------
//
//    @Nested @DisplayName("Duplicate email constraint")
//    class DuplicateEmail {
//
//        @Test
//        @DisplayName("should return 409 when creating customer with duplicate email")
//        void shouldReturn409OnDuplicateEmail() throws Exception {
//            customerRepository.save(Customer.builder()
//                    .customerId("existing-001")
//                    .firstname("Existing")
//                    .lastname("User")
//                    .email("duplicate@example.com")
//                    .build());
//
//            CustomerRequest duplicate = new CustomerRequest(
//                    null, "New", "User", "duplicate@example.com", null);
//
//            mockMvc.perform(post("/api/v1/customers")
//                            .contentType(MediaType.APPLICATION_JSON)
//                            .content(objectMapper.writeValueAsString(duplicate)))
//                    .andExpect(status().isConflict())
//                    .andExpect(jsonPath("$.errorCode", is("customer.email.already.exists")));
//        }
//    }
//
//    // -------------------------------------------------------
//    // Update
//    // -------------------------------------------------------
//
//    @Nested @DisplayName("Update customer")
//    class UpdateIntegration {
//
//        @Test
//        @DisplayName("PUT updates customer and returns 200 with updated data")
//        void shouldUpdateCustomerSuccessfully() throws Exception {
//            Customer saved = customerRepository.save(Customer.builder()
//                    .customerId("cust-update-001")
//                    .firstname("Original")
//                    .lastname("Name")
//                    .email("original@example.com")
//                    .build());
//
//            CustomerRequest updateRequest = new CustomerRequest(
//                    null, "Updated", "Name", "original@example.com", null);
//
//            mockMvc.perform(put("/api/v1/customers/" + saved.getCustomerId())
//                            .contentType(MediaType.APPLICATION_JSON)
//                            .content(objectMapper.writeValueAsString(updateRequest)))
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.firstname", is("Updated")));
//
//            // Verify DB change persisted
//            Customer fromDb = customerRepository.findById(saved.getCustomerId()).orElseThrow();
//            assertThat(fromDb.getFirstname()).isEqualTo("Updated");
//        }
//
//        @Test
//        @DisplayName("PUT returns 404 for non-existent customer")
//        void shouldReturn404OnUpdateOfNonExistent() throws Exception {
//            CustomerRequest request = new CustomerRequest(null, "X", "Y", "x@example.com", null);
//
//            mockMvc.perform(put("/api/v1/customers/ghost-id")
//                            .contentType(MediaType.APPLICATION_JSON)
//                            .content(objectMapper.writeValueAsString(request)))
//                    .andExpect(status().isNotFound())
//                    .andExpect(jsonPath("$.errorCode", is("customer.not.found.by.id")));
//        }
//    }
//
//    // -------------------------------------------------------
//    // Delete
//    // -------------------------------------------------------
//
//    @Nested @DisplayName("Delete customer")
//    class DeleteIntegration {
//
//        @Test
//        @DisplayName("DELETE removes customer and returns 204 No Content")
//        void shouldDeleteCustomerSuccessfully() throws Exception {
//            Customer saved = customerRepository.save(Customer.builder()
//                    .customerId("cust-del-001")
//                    .firstname("ToDelete")
//                    .lastname("User")
//                    .email("delete@example.com")
//                    .build());
//
//            mockMvc.perform(delete("/api/v1/customers/" + saved.getCustomerId()))
//                    .andExpect(status().isNoContent());
//
//            // Verify removed from DB
//            assertThat(customerRepository.findById(saved.getCustomerId())).isEmpty();
//        }
//
//        @Test
//        @DisplayName("DELETE returns 404 for non-existent customer")
//        void shouldReturn404WhenDeletingNonExistent() throws Exception {
//            mockMvc.perform(delete("/api/v1/customers/ghost-id"))
//                    .andExpect(status().isNotFound())
//                    .andExpect(jsonPath("$.errorCode", is("customer.not.found.by.id")));
//        }
//    }
//
//    // -------------------------------------------------------
//    // Validation
//    // -------------------------------------------------------
//
//    @Nested @DisplayName("Request validation")
//    class Validation {
//
//        @Test
//        @DisplayName("POST returns 400 when firstname is missing")
//        void shouldReturn400WhenFirstnameMissing() throws Exception {
//            String body = "{\"lastname\":\"Silva\",\"email\":\"test@example.com\"}";
//
//            mockMvc.perform(post("/api/v1/customers")
//                            .contentType(MediaType.APPLICATION_JSON)
//                            .content(body))
//                    .andExpect(status().isBadRequest());
//        }
//
//        @Test
//        @DisplayName("POST returns 400 when email format invalid")
//        void shouldReturn400WhenEmailInvalid() throws Exception {
//            CustomerRequest bad = new CustomerRequest(null, "Ana", "Silva", "not-valid", null);
//
//            mockMvc.perform(post("/api/v1/customers")
//                            .contentType(MediaType.APPLICATION_JSON)
//                            .content(objectMapper.writeValueAsString(bad)))
//                    .andExpect(status().isBadRequest());
//        }
//    }
//}
