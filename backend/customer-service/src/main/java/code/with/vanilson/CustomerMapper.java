package code.with.vanilson;

import code.with.vanilson.exception.CustomerBadRequestException;
import code.with.vanilson.exception.CustomerNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.*;

@Service
@Slf4j
public class CustomerMapper {

    private final static String CUSTOMER_CANNOT_BE_NULL = "Customer cannot be null";
    private final static String CUSTOMER_IS_NULL = "Customer is null: {}";

    /**
     * Converts a CustomerRequest object to a Customer entity.
     *
     * @param customerRequest The CustomerRequest object to be converted.
     * @return The Customer entity created from the CustomerRequest.
     * @throws CustomerBadRequestException if the CustomerRequest is null.
     */
    public Customer toCustomer(CustomerRequest customerRequest) {
        if (isNull(customerRequest)) {
            log.error("CustomerRequest is null: {}", customerRequest);
            throw new CustomerBadRequestException("CustomerRequest cannot be null");
        }

        return Customer.builder()
                .customerId(customerRequest.customerId())
                .firstname(customerRequest.firstname())
                .lastname(customerRequest.lastname())
                .email(customerRequest.email())
                .address(customerRequest.address())
                .build();
    }

    public CustomerRequest toCustomerRequest(Customer customerRequest) {
        if (isNull(customerRequest)) {
            log.error(CUSTOMER_IS_NULL, customerRequest);
            throw new CustomerBadRequestException(CUSTOMER_CANNOT_BE_NULL);
        }
        return new CustomerRequest(
                customerRequest.getCustomerId(),
                customerRequest.getFirstname(),
                customerRequest.getLastname(),
                customerRequest.getEmail(),
                customerRequest.getAddress()
        );

    }

    protected CustomerResponse fromCustomer(Customer customer) {
        if (isNull(customer)) {
            log.error(CUSTOMER_IS_NULL, customer);
            throw new CustomerBadRequestException(CUSTOMER_CANNOT_BE_NULL);
        }
        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getFirstname(),
                customer.getLastname(),
                customer.getEmail(),
                customer.getAddress()
        );
    }

    /**
     * Converts a list of Customer entities to a list of CustomerRequest objects.
     *
     * @param customers The list of Customer entities to be converted.
     * @return The list of CustomerRequest objects created from the Customer entities.
     */
    public List<CustomerRequest> toCustomers(List<Customer> customers) {
        return customers.stream()
                .map(customer1 -> new CustomerRequest(customer1.getCustomerId(),
                        customer1.getFirstname(), customer1.getLastname(), customer1.getEmail(),
                        customer1.getAddress()))
                .toList();
    }

    /**
     * Converts an Optional of Customer to a CustomerRequest by id.
     *
     * @param customer The Optional of Customer to be converted.
     * @return CustomerRequest if Customer is present, null otherwise.
     * @throws CustomerNotFoundException if Customer is not found.
     */
    protected CustomerRequest toCustomerById(Optional<Customer> customer) {
        if (customer.isPresent()) {
            Customer customerEntity = customer.get();
            log.info("CustomerEntity: {}", customerEntity);
            return new CustomerRequest(customerEntity.getCustomerId(),
                    customerEntity.getFirstname(),
                    customerEntity.getLastname(),
                    customerEntity.getEmail(),
                    customerEntity.getAddress());
        } else {
            log.error("Customer not found");
            throw new CustomerNotFoundException("Customer not found");
        }
    }

}
