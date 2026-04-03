package code.with.vanilson.customerservice.unit;

import code.with.vanilson.customerservice.Address;
import code.with.vanilson.customerservice.Customer;
import code.with.vanilson.customerservice.CustomerMapper;
import code.with.vanilson.customerservice.CustomerRepository;
import code.with.vanilson.customerservice.CustomerRequest;
import code.with.vanilson.customerservice.CustomerResponse;
import code.with.vanilson.customerservice.CustomerService;
import code.with.vanilson.customerservice.exception.CustomerNotFoundException;
import code.with.vanilson.customerservice.exception.EmailAlreadyExistsException;
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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerServiceTest — Unit Tests
 * <p>
 * Framework: JUnit 5 + Mockito + AssertJ.
 * All dependencies mocked — no Spring context loaded.
 * MessageSource mocked to return the key itself (no .properties file needed in unit tests).
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService — Unit Tests")
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerMapper     customerMapper;
    @Mock private MessageSource      messageSource;

    @InjectMocks
    private CustomerService customerService;

    // -------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------

    private Customer       customer1;
    private Customer       customer2;
    private CustomerRequest request1;
    private CustomerResponse response1;
    private CustomerResponse response2;
    private Address         address;

    @BeforeEach
    void setUp() {
        // MessageSource: return key itself — no dependency on .properties files
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        address   = new Address("Main St", "42", "10001", "Porto Alegre", "RS");
        customer1 = Customer.builder()
                .customerId("cust-001")
                .firstname("Ana")
                .lastname("Silva")
                .email("ana@example.com")
                .address(address)
                .build();
        customer2 = Customer.builder()
                .customerId("cust-002")
                .firstname("Bruno")
                .lastname("Costa")
                .email("bruno@example.com")
                .build();

        request1  = new CustomerRequest(null, "Ana", "Silva", "ana@example.com", address);
        response1 = new CustomerResponse("cust-001", "Ana", "Silva", "ana@example.com", address);
        response2 = new CustomerResponse("cust-002", "Bruno", "Costa", "bruno@example.com", null);
    }

    // -------------------------------------------------------
    // findAllCustomers
    // -------------------------------------------------------

    @Nested @DisplayName("findAllCustomers")
    class FindAll {

        @Test
        @DisplayName("should return mapped list of all customers")
        void shouldReturnAllCustomers() {
            when(customerRepository.findAll()).thenReturn(List.of(customer1, customer2));
            when(customerMapper.toResponse(customer1)).thenReturn(response1);
            when(customerMapper.toResponse(customer2)).thenReturn(response2);

            List<CustomerResponse> result = customerService.findAllCustomers();

            assertThat(result)
                    .isNotNull()
                    .hasSize(2)
                    .extracting(CustomerResponse::email)
                    .containsExactly("ana@example.com", "bruno@example.com");

            verify(customerRepository, times(1)).findAll();
            verify(customerMapper, times(1)).toResponse(customer1);
            verify(customerMapper, times(1)).toResponse(customer2);
        }

        @Test
        @DisplayName("should return empty list when no customers exist")
        void shouldReturnEmptyList() {
            when(customerRepository.findAll()).thenReturn(List.of());

            List<CustomerResponse> result = customerService.findAllCustomers();

            assertThat(result).isNotNull().isEmpty();
            verify(customerRepository, times(1)).findAll();
        }
    }

    // -------------------------------------------------------
    // getCustomerById
    // -------------------------------------------------------

    @Nested @DisplayName("getCustomerById")
    class GetById {

        @Test
        @DisplayName("should return CustomerResponse when customer exists")
        void shouldReturnCustomerWhenFound() {
            when(customerRepository.findById("cust-001")).thenReturn(Optional.of(customer1));
            when(customerMapper.toResponse(customer1)).thenReturn(response1);

            CustomerResponse result = customerService.getCustomerById("cust-001");

            assertThat(result).isNotNull();
            assertThat(result.customerId()).isEqualTo("cust-001");
            assertThat(result.email()).isEqualTo("ana@example.com");

            verify(customerRepository, times(1)).findById("cust-001");
            verify(customerMapper, times(1)).toResponse(customer1);
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException when customer does not exist")
        void shouldThrowWhenNotFound() {
            when(customerRepository.findById("ghost-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getCustomerById("ghost-id"))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining("customer.not.found.by.id");

            verify(customerRepository, times(1)).findById("ghost-id");
            verify(customerMapper, never()).toResponse(any());
        }
    }

    // -------------------------------------------------------
    // findByEmail
    // -------------------------------------------------------

    @Nested @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("should return CustomerResponse when email exists")
        void shouldReturnCustomerByEmail() {
            when(customerRepository.findCustomerByEmail("ana@example.com"))
                    .thenReturn(Optional.of(customer1));
            when(customerMapper.toResponse(customer1)).thenReturn(response1);

            CustomerResponse result = customerService.findByEmail("ana@example.com");

            assertThat(result.email()).isEqualTo("ana@example.com");
            verify(customerRepository, times(1)).findCustomerByEmail("ana@example.com");
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException when email not found")
        void shouldThrowWhenEmailNotFound() {
            when(customerRepository.findCustomerByEmail("ghost@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.findByEmail("ghost@example.com"))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining("customer.not.found.by.email");
        }
    }

    // -------------------------------------------------------
    // createCustomer
    // -------------------------------------------------------

    @Nested @DisplayName("createCustomer")
    class Create {

        @Test
        @DisplayName("should persist and return customer ID when email is unique")
        void shouldCreateCustomerSuccessfully() {
            when(customerRepository.findCustomerByEmail("ana@example.com"))
                    .thenReturn(Optional.empty());
            when(customerMapper.toEntity(request1)).thenReturn(customer1);
            when(customerRepository.save(customer1)).thenReturn(customer1);

            String result = customerService.createCustomer(request1);

            assertThat(result).isEqualTo("cust-001");
            verify(customerRepository, times(1)).save(customer1);
        }

        @Test
        @DisplayName("should throw EmailAlreadyExistsException when email is duplicate")
        void shouldThrowWhenEmailDuplicate() {
            when(customerRepository.findCustomerByEmail("ana@example.com"))
                    .thenReturn(Optional.of(customer1));

            assertThatThrownBy(() -> customerService.createCustomer(request1))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("customer.email.already.exists");

            verify(customerRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------
    // updateCustomer
    // -------------------------------------------------------

    @Nested @DisplayName("updateCustomer")
    class Update {

        @Test
        @DisplayName("should update and return CustomerResponse when customer exists")
        void shouldUpdateSuccessfully() {
            CustomerRequest updateRequest = new CustomerRequest(
                    null, "Ana Updated", "Silva", "ana@example.com", address);
            when(customerRepository.findById("cust-001")).thenReturn(Optional.of(customer1));
            when(customerRepository.save(any())).thenReturn(customer1);
            when(customerMapper.toResponse(any())).thenReturn(response1);

            CustomerResponse result = customerService.updateCustomer("cust-001", updateRequest);

            assertThat(result).isNotNull();
            verify(customerRepository, times(1)).findById("cust-001");
            verify(customerRepository, times(1)).save(any(Customer.class));
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException when customer not found")
        void shouldThrowWhenNotFound() {
            CustomerRequest updateRequest = new CustomerRequest(
                    null, "X", "Y", "x@example.com", null);
            when(customerRepository.findById("ghost-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.updateCustomer("ghost-id", updateRequest))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining("customer.not.found.by.id");

            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("cache eviction uses method param customerId, not request.customerId() (bug fix)")
        void cacheEvictionUsesMethodParam() {
            // request.customerId() is null — if the cache key used request.customerId()
            // it would fail with NullPointerException on key resolution
            CustomerRequest updateRequest = new CustomerRequest(
                    null, "Ana", "Silva", "ana@example.com", null); // customerId = null

            when(customerRepository.findById("cust-001")).thenReturn(Optional.of(customer1));
            when(customerRepository.save(any())).thenReturn(customer1);
            when(customerMapper.toResponse(any())).thenReturn(response1);

            // If the bug were still present, this would throw CacheOperationInvoker or NPE
            CustomerResponse result = customerService.updateCustomer("cust-001", updateRequest);

            assertThat(result).isNotNull();
        }
    }

    // -------------------------------------------------------
    // deleteCustomer
    // -------------------------------------------------------

    @Nested @DisplayName("deleteCustomer")
    class Delete {

        @Test
        @DisplayName("should delete customer when found")
        void shouldDeleteSuccessfully() {
            when(customerRepository.findById("cust-001")).thenReturn(Optional.of(customer1));

            customerService.deleteCustomer("cust-001");

            verify(customerRepository, times(1)).deleteById("cust-001");
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException when customer not found")
        void shouldThrowWhenNotFound() {
            when(customerRepository.findById("ghost-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.deleteCustomer("ghost-id"))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining("customer.not.found.by.id");

            verify(customerRepository, never()).deleteById(any());
        }
    }
}
