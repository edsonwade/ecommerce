package code.with.vanilson.customerservice;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/customers")
@Slf4j
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * Endpoint to get all customers and return them as a ResponseEntity with a list of CustomerRequest objects.
     *
     * @return ResponseEntity with the list of CustomerRequest objects representing all customers.
     */
    @GetMapping
    public ResponseEntity<List<CustomerRequest>> getCustomers() {
        log.info("get customers");
        return ResponseEntity.ok(customerService.findAllCustomers());
    }

    /**
     * Endpoint to get all customers and return them as a ResponseEntity with a list of CustomerResponse objects.
     *
     * @return ResponseEntity with the list of CustomerResponse objects representing all customers.
     */
    @GetMapping("/list-customers")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        log.info("get all customers");
        return ResponseEntity.ok(customerService.listAllCustomers());
    }

    /**
     * Retrieves a CustomerRequest by ID.
     *
     * @param customerId The ID of the customer to retrieve.
     * @return ResponseEntity with the CustomerRequest if found, 404 Not Found if not found.
     */
    @GetMapping(value = "/{customer-id}")
    public ResponseEntity<CustomerRequest> getCustomersById(@PathVariable(name = "customer-id") String customerId) {
        log.info("Get customer by id: {}", customerId);
        return ResponseEntity.ok(customerService.findCustomerById(customerId));
    }

    /**
     * Retrieves a CustomerRequest by ID.
     *
     * @param customerId The ID of the customer to retrieve.
     * @return ResponseEntity with the CustomerRequest if found, 404 Not Found if not found.
     */
    @GetMapping(value = "/response-customer/{customer-id}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable(name = "customer-id") String customerId) {
        log.info("Get customer response by id: {}", customerId);
        return ResponseEntity.ok(customerService.getCustomerById(customerId));
    }

    /**
     * Retrieves a CustomerRequest by email.
     *
     * @param customerEmail The email of the customer to retrieve.
     * @return ResponseEntity with the CustomerRequest if found, 404 Not Found if not found.
     */
    @GetMapping(value = "/email/{customer-email}")
    public ResponseEntity<CustomerRequest> getCustomersByEmail(
            @PathVariable(name = "customer-email") String customerEmail) {
        log.info("Get customer by email: {}", customerEmail);
        return ResponseEntity.ok(customerService.findCustomerByEmail(customerEmail));
    }

    /**
     * Endpoint to add a new customer.
     *
     * @param customerRequest The CustomerRequest object containing customer details.
     * @return ResponseEntity with the customer ID of the created customer.
     */
    @PostMapping( "/create-customer")
    public ResponseEntity<String> addCustomer(@RequestBody @Valid CustomerRequest customerRequest) {
        // Return ResponseEntity with 201 Created statuses
        log.info("add customer: {}", customerRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(customerService.createCustomer(customerRequest));
    }

    @PutMapping(value = "/update-customer/{id}")
    public ResponseEntity<CustomerRequest> updateCustomer(@PathVariable("id") String id,
                                                          @RequestBody @Valid Customer customerRequest) {
        log.info("update customer: {}", customerRequest);
        var customer = customerService.updateCustomer(id, customerRequest);
        return ResponseEntity.ok(customer);
    }

    /**
     * Endpoint to delete a customer by ID.
     *
     * @param customerId The ID of the customer to delete.
     * @return ResponseEntity with 204 No Content if successful, or 404 Not Found if the customer is not found.
     */
    @DeleteMapping(value = "/delete-customer/{customer-id}")
    public ResponseEntity<Void> deleteCustomerById(@PathVariable(name = "customer-id") String customerId) {
        customerService.deleteCustomer(customerId);
        return ResponseEntity.accepted().build();
    }

}
