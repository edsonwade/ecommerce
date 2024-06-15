package code.with.vanilson.customerservice;

import code.with.vanilson.customerservice.exception.CustomerNotFoundException;
import code.with.vanilson.customerservice.exception.EmailAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.text.MessageFormat.*;

@Slf4j
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerMapper customerMapper, CustomerRepository customerRepository,
                           MongoTemplate mongoTemplate) {
        this.customerMapper = customerMapper;
        this.customerRepository = customerRepository;
    }

    /**
     * Retrieves all customers from the repository and maps them to a list of CustomerRequest objects.
     *
     * @return The list of CustomerRequest objects representing all customers.
     */
    public List<CustomerRequest> findAllCustomers() {
        var customers = customerRepository.findAll();
        log.info("List of Customers: {}", customers);
        return customerMapper.toCustomers(customers);
    }

    /**
     * Retrieves a list of all customers and maps them to CustomerResponse objects.
     *
     * @return The list of CustomerResponse objects representing all customers.
     */
    public List<CustomerResponse> listAllCustomers() {
        var customers = customerRepository.findAll()
                .stream()
                .map(customerMapper::fromCustomer)
                .toList();
        log.info("Find all Customers: {}", customers);
        return customers;
    }

    /**
     * Finds a customer by ID and returns the corresponding CustomerRequest.
     *
     * @param customerId The ID of the customer to find.
     * @return The CustomerRequest object representing the customer found by ID.
     * @throws CustomerNotFoundException if the customer is not found.
     */
    public CustomerRequest findCustomerById(String customerId) {
        var customer = customerRepository.findByCustomerId(customerId);
        log.info("Customer found: {}", customer);
        if (customer.isPresent()) {
            return customerMapper.toCustomerById(customer);
        }
        throw new CustomerNotFoundException(format("Customer With customerId {0} not found", customerId));
    }

    /**
     * Retrieves a CustomerResponse by ID.
     *
     * @param customerId The ID of the customer to retrieve.
     * @return The CustomerResponse if found, or throws CustomerNotFoundException if not found.
     */
    public CustomerResponse getCustomerById(String customerId) {
        return customerRepository.findById(customerId)
                .map(customerMapper::fromCustomer)
                .orElseThrow(() -> new CustomerNotFoundException(
                        format("Customer With id {0} not found", customerId)));
    }

    /**
     * Finds a customer by email and returns the corresponding CustomerRequest.
     *
     * @param email The email of the customer to find.
     * @return The CustomerRequest object representing the customer found by email.
     * @throws CustomerNotFoundException if the customer is not found.
     */

    public CustomerRequest findCustomerByEmail(String email) {
        var customer = customerRepository.findCustomerByEmail(email)
                .orElseThrow(() -> new CustomerNotFoundException(format("Customer With id {0} not " +
                        "found", email)));
        return customerMapper.toCustomerById(Optional.ofNullable(customer));
    }

    /**
     * Creates a new customer based on the provided CustomerRequest.
     *
     * @param customerRequest The CustomerRequest object containing customer details.
     * @return The customer ID of the created customer.
     */
    public String createCustomer(CustomerRequest customerRequest) {
        try {
            var customers = customerRepository.findCustomerByEmail(customerRequest.email());
            if (customers.isPresent()) {
                throw new EmailAlreadyExistsException(
                        MessageFormat.format("Customer With email {0} already exists", customerRequest.email()));
            }
            var customer = customerRepository.save(customerMapper.toCustomer(customerRequest));
            log.info("Customer created: {}", customer);
            return customer.getCustomerId();
        } catch (IncorrectResultSizeDataAccessException ex) {
            throw new EmailAlreadyExistsException(
                    MessageFormat.format("Customer With email {0} already exists", customerRequest.email()));
        }
    }

    /**
     * Requests to update a Customer with the provided CustomerRequest details.
     *
     * @param customerRequest The CustomerRequest object containing updated customer details.
     */
    public void requestToUpdateCustomer(CustomerRequest customerRequest) {
        var customer = customerRepository.findById(customerRequest.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(
                        format("Customer with id {0} not found", customerRequest.customerId())));

        mergerCustomer(customer, customerRequest);
        customerRepository.save(customer);

    }

    /**
     * Merges the updated details from CustomerRequest into the existing Customer entity.
     *
     * @param customer        The existing Customer entity to be updated.
     * @param customerRequest The CustomerRequest object with updated details.
     */
    private void mergerCustomer(Customer customer, CustomerRequest customerRequest) {
        if (StringUtils.isNotBlank(customerRequest.firstname())) {
            customer.setFirstname(customerRequest.firstname());
        }
        if (StringUtils.isNotBlank(customerRequest.lastname())) {
            customer.setLastname(customerRequest.lastname());
        }
        if (StringUtils.isNotBlank(customerRequest.email())) {
            customer.setEmail(customerRequest.email());
        }
        if (Objects.nonNull(customerRequest.address())) {
            customer.setAddress(customerRequest.address());
        }

    }
    /**
     * Updates a Customer entity with new details and maps it to a CustomerRequest.
     *
     * @param customerId             The ID of the customer to update.
     * @param updatedCustomerRequest The updated details for the customer.
     * @return The updated CustomerRequest entity.
     */
    public CustomerRequest updateCustomer(String customerId, Customer updatedCustomerRequest) {
        // Check if the customer ID exists
        Customer existingCustomer = customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        // Validate if the email already exists
        if (!existingCustomer.getEmail().equals(updatedCustomerRequest.getEmail())
                && customerRepository.existsById(updatedCustomerRequest.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        // Update customer details with the new information
        existingCustomer.setFirstname(updatedCustomerRequest.getFirstname());
        existingCustomer.setLastname(updatedCustomerRequest.getLastname());
        existingCustomer.setEmail(updatedCustomerRequest.getEmail());
        existingCustomer.setAddress(updatedCustomerRequest.getAddress());

        // Save the updated customer
        Customer updatedCustomer = customerRepository.save(existingCustomer);

        // Map the updated customer to CustomerRequest
        return customerMapper.toCustomerRequest(updatedCustomer);
    }

    /**
     * Deletes a customer by ID.
     *
     * @param customerId The ID of the customer to delete.
     * @throws CustomerNotFoundException if the customer is not found.
     */
    public void deleteCustomer(String customerId) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        format("Customer with id {0} not found", customerId)));
        customerRepository.deleteById(customer.getCustomerId());

    }

}
