package code.with.vanilson.customerservice.unit;

import code.with.vanilson.customerservice.*;
import code.with.vanilson.customerservice.exception.CustomerNotFoundException;
import code.with.vanilson.customerservice.exception.EmailAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomerService — Application Layer.
 * <p>
 * Covers: happy path, failure/invalid input, exception handling, edge cases.
 * Uses JUnit 5 + Mockito + AssertJ as per Skill.md requirements.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService Unit Tests")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private CustomerService customerService;

    // ---- Test data ----
    private Customer customer;
    private CustomerRequest customerRequest;
    private CustomerResponse customerResponse;
    private Address address;

    @BeforeEach
    void setUp() {
        address = new Address("Main St", "42", "12345", "US", "New York");

        customer = Customer.builder()
                .customerId("cust-001")
                .firstname("John")
                .lastname("Doe")
                .email("john.doe@example.com")
                .address(address)
                .build();

        customerRequest = new CustomerRequest(
                "cust-001", "John", "Doe", "john.doe@example.com", address);

        customerResponse = new CustomerResponse(
                "cust-001", "John", "Doe", "john.doe@example.com", address);

        // Stub MessageSource to return the key itself for all calls
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // =============================================================
    // findAllCustomers()
    // =============================================================
    @Nested
    @DisplayName("findAllCustomers()")
    class FindAllCustomers {

        @Test
        @DisplayName("given_customers_exist_when_findAll_then_return_list")
        void given_customers_exist_when_findAll_then_return_list() {
            // Arrange
            when(customerRepository.findAll()).thenReturn(List.of(customer));
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

            // Act
            List<CustomerResponse> result = customerService.findAllCustomers();

            // Assert
            assertThat(result)
                    .isNotNull()
                    .hasSize(1)
                    .first()
                    .satisfies(r -> {
                        assertThat(r.customerId()).isEqualTo("cust-001");
                        assertThat(r.email()).isEqualTo("john.doe@example.com");
                    });

            verify(customerRepository).findAll();
            verify(customerMapper).toResponse(customer);
        }

        @Test
        @DisplayName("given_no_customers_when_findAll_then_return_empty_list")
        void given_no_customers_when_findAll_then_return_empty_list() {
            // Arrange
            when(customerRepository.findAll()).thenReturn(List.of());

            // Act
            List<CustomerResponse> result = customerService.findAllCustomers();

            // Assert
            assertThat(result).isNotNull().isEmpty();
            verify(customerRepository).findAll();
            verifyNoInteractions(customerMapper);
        }
    }

    // =============================================================
    // getCustomerById()
    // =============================================================
    @Nested
    @DisplayName("getCustomerById()")
    class GetCustomerById {

        @Test
        @DisplayName("given_valid_id_when_getById_then_return_customer")
        void given_valid_id_when_getById_then_return_customer() {
            // Arrange
            when(customerRepository.findById("cust-001")).thenReturn(Optional.of(customer));
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

            // Act
            CustomerResponse result = customerService.getCustomerById("cust-001");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.customerId()).isEqualTo("cust-001");
            assertThat(result.firstname()).isEqualTo("John");
            assertThat(result.lastname()).isEqualTo("Doe");
            assertThat(result.email()).isEqualTo("john.doe@example.com");

            verify(customerRepository).findById("cust-001");
            verify(customerMapper).toResponse(customer);
        }

        @Test
        @DisplayName("given_invalid_id_when_getById_then_throw_CustomerNotFoundException")
        void given_invalid_id_when_getById_then_throw_CustomerNotFoundException() {
            // Arrange
            when(customerRepository.findById("invalid-id")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> customerService.getCustomerById("invalid-id"))
                    .isInstanceOf(CustomerNotFoundException.class);

            verify(customerRepository).findById("invalid-id");
            verifyNoInteractions(customerMapper);
        }
    }

    // =============================================================
    // findByEmail()
    // =============================================================
    @Nested
    @DisplayName("findByEmail()")
    class FindByEmail {

        @Test
        @DisplayName("given_valid_email_when_findByEmail_then_return_customer")
        void given_valid_email_when_findByEmail_then_return_customer() {
            // Arrange
            when(customerRepository.findCustomerByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(customer));
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

            // Act
            CustomerResponse result = customerService.findByEmail("john.doe@example.com");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.email()).isEqualTo("john.doe@example.com");

            verify(customerRepository).findCustomerByEmail("john.doe@example.com");
        }

        @Test
        @DisplayName("given_unknown_email_when_findByEmail_then_throw_CustomerNotFoundException")
        void given_unknown_email_when_findByEmail_then_throw_CustomerNotFoundException() {
            // Arrange
            when(customerRepository.findCustomerByEmail("unknown@example.com"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> customerService.findByEmail("unknown@example.com"))
                    .isInstanceOf(CustomerNotFoundException.class);

            verify(customerRepository).findCustomerByEmail("unknown@example.com");
        }
    }

    // =============================================================
    // createCustomer()
    // =============================================================
    @Nested
    @DisplayName("createCustomer()")
    class CreateCustomer {

        @Test
        @DisplayName("given_valid_request_when_create_then_return_customerId")
        void given_valid_request_when_create_then_return_customerId() {
            // Arrange
            when(customerRepository.findCustomerByEmail("john.doe@example.com"))
                    .thenReturn(Optional.empty());
            when(customerMapper.toEntity(customerRequest)).thenReturn(customer);
            when(customerRepository.save(customer)).thenReturn(customer);

            // Act
            String result = customerService.createCustomer(customerRequest);

            // Assert
            assertThat(result).isEqualTo("cust-001");

            verify(customerRepository).findCustomerByEmail("john.doe@example.com");
            verify(customerMapper).toEntity(customerRequest);
            verify(customerRepository).save(customer);
        }

        @Test
        @DisplayName("given_duplicate_email_when_create_then_throw_EmailAlreadyExistsException")
        void given_duplicate_email_when_create_then_throw_EmailAlreadyExistsException() {
            // Arrange
            when(customerRepository.findCustomerByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(customer));

            // Act & Assert
            assertThatThrownBy(() -> customerService.createCustomer(customerRequest))
                    .isInstanceOf(EmailAlreadyExistsException.class);

            verify(customerRepository).findCustomerByEmail("john.doe@example.com");
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("given_IncorrectResultSizeDataAccessException_when_create_then_throw_EmailAlreadyExistsException")
        void given_IncorrectResultSizeDataAccessException_when_create_then_throw_EmailAlreadyExistsException() {
            // Arrange — simulates MongoDB returning multiple results for the email
            when(customerRepository.findCustomerByEmail("john.doe@example.com"))
                    .thenThrow(new IncorrectResultSizeDataAccessException(1));

            // Act & Assert
            assertThatThrownBy(() -> customerService.createCustomer(customerRequest))
                    .isInstanceOf(EmailAlreadyExistsException.class);
        }
    }

    // =============================================================
    // updateCustomer()
    // =============================================================
    @Nested
    @DisplayName("updateCustomer()")
    class UpdateCustomer {

        @Test
        @DisplayName("given_valid_update_when_update_then_return_updated_customer")
        void given_valid_update_when_update_then_return_updated_customer() {
            // Arrange
            CustomerRequest updateRequest = new CustomerRequest(
                    null, "Jane", "Smith", "jane.smith@example.com", address);
            CustomerResponse updatedResponse = new CustomerResponse(
                    "cust-001", "Jane", "Smith", "jane.smith@example.com", address);

            when(customerRepository.findById("cust-001")).thenReturn(Optional.of(customer));
            when(customerRepository.save(any(Customer.class))).thenReturn(customer);
            when(customerMapper.toResponse(any(Customer.class))).thenReturn(updatedResponse);

            // Act
            CustomerResponse result = customerService.updateCustomer("cust-001", updateRequest);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.firstname()).isEqualTo("Jane");
            assertThat(result.lastname()).isEqualTo("Smith");

            verify(customerRepository).findById("cust-001");
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("given_nonexistent_id_when_update_then_throw_CustomerNotFoundException")
        void given_nonexistent_id_when_update_then_throw_CustomerNotFoundException() {
            // Arrange
            when(customerRepository.findById("invalid-id")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> customerService.updateCustomer("invalid-id", customerRequest))
                    .isInstanceOf(CustomerNotFoundException.class);

            verify(customerRepository).findById("invalid-id");
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("given_partial_update_when_update_then_only_non_blank_fields_are_merged")
        void given_partial_update_when_update_then_only_non_blank_fields_are_merged() {
            // Arrange — request with only firstname, blank/null other fields
            CustomerRequest partialRequest = new CustomerRequest(
                    null, "UpdatedName", "", null, null);

            when(customerRepository.findById("cust-001")).thenReturn(Optional.of(customer));
            when(customerRepository.save(any(Customer.class))).thenReturn(customer);
            when(customerMapper.toResponse(any(Customer.class))).thenReturn(customerResponse);

            // Act
            customerService.updateCustomer("cust-001", partialRequest);

            // Assert — verify save was called (non-blank fields merged)
            verify(customerRepository).save(argThat(c -> {
                // firstname should be updated
                assertThat(c.getFirstname()).isEqualTo("UpdatedName");
                // lastname should keep original value (blank in request)
                assertThat(c.getLastname()).isEqualTo("Doe");
                // email should keep original value (null in request is not "" but
                // the empty string "" is not "hasText")
                assertThat(c.getEmail()).isEqualTo("john.doe@example.com");
                return true;
            }));
        }
    }

    // =============================================================
    // deleteCustomer()
    // =============================================================
    @Nested
    @DisplayName("deleteCustomer()")
    class DeleteCustomer {

        @Test
        @DisplayName("given_valid_id_when_delete_then_customer_is_removed")
        void given_valid_id_when_delete_then_customer_is_removed() {
            // Arrange
            when(customerRepository.findById("cust-001")).thenReturn(Optional.of(customer));

            // Act
            customerService.deleteCustomer("cust-001");

            // Assert
            verify(customerRepository).findById("cust-001");
            verify(customerRepository).deleteById("cust-001");
        }

        @Test
        @DisplayName("given_nonexistent_id_when_delete_then_throw_CustomerNotFoundException")
        void given_nonexistent_id_when_delete_then_throw_CustomerNotFoundException() {
            // Arrange
            when(customerRepository.findById("invalid-id")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> customerService.deleteCustomer("invalid-id"))
                    .isInstanceOf(CustomerNotFoundException.class);

            verify(customerRepository).findById("invalid-id");
            verify(customerRepository, never()).deleteById(any());
        }
    }
}
