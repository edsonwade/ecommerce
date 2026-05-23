package code.with.vanilson.customerservice.bdd;

import code.with.vanilson.customerservice.Customer;
import code.with.vanilson.customerservice.CustomerMapper;
import code.with.vanilson.customerservice.CustomerRepository;
import code.with.vanilson.customerservice.CustomerRequest;
import code.with.vanilson.customerservice.CustomerResponse;
import code.with.vanilson.customerservice.CustomerService;
import code.with.vanilson.customerservice.exception.CustomerNotFoundException;
import code.with.vanilson.customerservice.exception.EmailAlreadyExistsException;
import code.with.vanilson.customerservice.kafka.CustomerProfileProducer;
import code.with.vanilson.customerservice.kafka.UserRegisteredConsumer;
import code.with.vanilson.customerservice.kafka.UserRegisteredEvent;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class CustomerStepDefinitions {

    private CustomerService customerService;
    private CustomerRepository customerRepository;
    private CustomerMapper customerMapper;
    private MessageSource messageSource;
    private CustomerProfileProducer customerProfileProducer;
    private UserRegisteredConsumer userRegisteredConsumer;
    private Acknowledgment ack;

    private CustomerRequest customerRequest;
    private String savedCustomerId;
    private String ensuredCustomerId;
    private Exception caughtException;
    private Customer mockedCustomer;
    private boolean duplicateScenario;

    @Before
    public void setUp() {
        customerRepository = Mockito.mock(CustomerRepository.class);
        customerMapper = Mockito.mock(CustomerMapper.class);
        messageSource = Mockito.mock(MessageSource.class);
        customerProfileProducer = Mockito.mock(CustomerProfileProducer.class);
        ack = Mockito.mock(Acknowledgment.class);

        customerService = new CustomerService(customerMapper, customerRepository, messageSource,
                customerProfileProducer);
        userRegisteredConsumer = new UserRegisteredConsumer(customerRepository);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        caughtException = null;
        savedCustomerId = null;
        ensuredCustomerId = null;
    }

    @Given("a valid customer request for {string}")
    public void a_valid_customer_request_for(String email) {
        customerRequest = new CustomerRequest("id-123", "John", "Doe", email, null);

        mockedCustomer = Customer.builder()
                .customerId("id-123")
                .firstname("John")
                .lastname("Doe")
                .email(email)
                .build();

        CustomerResponse response = new CustomerResponse("id-123", "John", "Doe", email, null);

        when(customerMapper.toEntity(any())).thenReturn(mockedCustomer);
        when(customerMapper.toResponse(any())).thenReturn(response);
    }

    @Given("the email is not already in use")
    public void the_email_is_not_in_use() {
        when(customerRepository.save(any())).thenReturn(mockedCustomer);
        duplicateScenario = false;
    }

    @Given("the email is already in use")
    public void the_email_is_already_in_use() {
        when(customerRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("Unique index violation"));
        duplicateScenario = true;
    }

    @When("the customer is created")
    public void the_customer_is_created() {
        try {
            savedCustomerId = customerService.createCustomer(customerRequest);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("a customer ID is returned")
    public void a_customer_ID_is_returned() {
        assertThat(caughtException).isNull();
        assertThat(savedCustomerId).isEqualTo("id-123");
    }

    @Then("the customer details can be retrieved")
    public void the_customer_details_can_be_retrieved() {
        when(customerRepository.findById("id-123")).thenReturn(Optional.of(mockedCustomer));
        CustomerResponse response = customerService.getCustomerById("id-123");
        assertThat(response.customerId()).isEqualTo("id-123");
    }

    @Then("the system rejects the request with a duplicate email error")
    public void the_system_rejects_request_with_error() {
        assertThat(caughtException).isNotNull().isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Given("a customer with ID {string} exists")
    public void a_customer_with_ID_exists(String id) {
        Customer existing = Customer.builder().customerId(id).build();
        when(customerRepository.existsById(id)).thenReturn(true);
        when(customerRepository.findById(id)).thenReturn(Optional.of(existing));
        this.savedCustomerId = id;
    }

    @Given("no customer with ID {string} exists")
    public void no_customer_with_ID_exists(String id) {
        when(customerRepository.existsById(id)).thenReturn(false);
    }

    @When("the customer is ensured with ID {string} and email {string}")
    public void the_customer_is_ensured(String id, String email) {
        CustomerRequest ensureRequest = new CustomerRequest(id, "Test", "User", email, null);

        Customer entity = Customer.builder()
                .customerId(id).firstname("Test").lastname("User").email(email).build();
        lenient().when(customerMapper.toEntity(any())).thenReturn(entity);
        lenient().when(customerRepository.save(any())).thenReturn(entity);

        try {
            ensuredCustomerId = customerService.ensureCustomer(ensureRequest);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("the returned customer ID is {string}")
    public void the_returned_customer_ID_is(String expectedId) {
        assertThat(caughtException).isNull();
        assertThat(ensuredCustomerId).isEqualTo(expectedId);
    }

    @When("the customer is deleted")
    public void the_customer_is_deleted() {
        customerService.deleteCustomer(savedCustomerId);
    }

    @Then("the customer can no longer be retrieved")
    public void the_customer_cannot_be_retrieved() {
        when(customerRepository.findById(savedCustomerId)).thenReturn(Optional.empty());
        try {
            customerService.getCustomerById(savedCustomerId);
            caughtException = null;
        } catch (Exception e) {
            caughtException = e;
        }
        assertThat(caughtException).isNotNull().isInstanceOf(CustomerNotFoundException.class);
    }

    // -------------------------------------------------------
    // Phase 2 BDD step definitions
    // -------------------------------------------------------

    @Then("a customer.profile CREATED event is published")
    public void a_customer_profile_created_event_is_published() {
        verify(customerProfileProducer).publishProfileEvent(any(Customer.class), eq("CREATED"));
    }

    @When("a user.registered event is received for {string} with email {string}")
    public void a_user_registered_event_is_received(String userId, String email) {
        UserRegisteredEvent event = new UserRegisteredEvent(
                "evt-bdd-001", userId, "Test", "User", email, "default", Instant.now(), 1);
        userRegisteredConsumer.onUserRegistered(event, ack);
    }

    @Then("a customer profile is created for {string}")
    public void a_customer_profile_is_created_for(String userId) {
        verify(customerRepository).save(argThat(c ->
                c instanceof Customer && userId.equals(((Customer) c).getCustomerId())));
        verify(ack).acknowledge();
    }

    @Then("no duplicate customer is created for {string}")
    public void no_duplicate_customer_is_created_for(String userId) {
        verify(customerRepository, never()).save(any());
        verify(ack).acknowledge();
    }
}
