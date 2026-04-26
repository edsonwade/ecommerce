package code.with.vanilson.customerservice;

import code.with.vanilson.customerservice.exception.CustomerNotFoundException;
import code.with.vanilson.customerservice.exception.EmailAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * CustomerService — Application Layer
 * <p>
 * Core business logic for customer profile management.
 * <p>
 * Design decisions:
 * - Redis L2 cache: reads cached 10 minutes (customers change rarely)
 * - Every write evicts the relevant cache entries
 * - All messages resolved from messages.properties via MessageSource
 * - Single Responsibility (SOLID-S): each method does one thing
 * <p>
 * BUG FIXED: @CacheEvict on updateCustomer used #request.customerId() which can be null
 * (CustomerRequest.customerId is optional on update). Fixed to use #customerId param.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Slf4j
@Service
public class CustomerService {

    private static final String CACHE_CUSTOMERS    = "customers";
    private static final String CACHE_CUSTOMER_LIST = "customer-list";

    private final CustomerRepository customerRepository;
    private final CustomerMapper     customerMapper;
    private final MessageSource      messageSource;

    public CustomerService(CustomerMapper customerMapper,
                           CustomerRepository customerRepository,
                           MessageSource messageSource) {
        this.customerMapper     = customerMapper;
        this.customerRepository = customerRepository;
        this.messageSource      = messageSource;
    }

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    /**
     * Returns all customers, cached for 10 minutes.
     */
    @Cacheable(value = CACHE_CUSTOMER_LIST, unless = "#result == null || #result.isEmpty()")
    public List<CustomerResponse> findAllCustomers() {
        List<CustomerResponse> customers = customerRepository.findAll()
                .stream()
                .map(customerMapper::toResponse)
                .toList();
        log.info(msg("customer.log.all.found", customers.size()));
        return customers;
    }

    /**
     * Returns a customer by their ID.
     *
     * @param customerId the customer's unique ID
     * @return CustomerResponse DTO
     * @throws CustomerNotFoundException if no customer exists with this ID
     */
    @Cacheable(value = CACHE_CUSTOMERS, key = "#customerId")
    public CustomerResponse getCustomerById(String customerId) {
        return customerRepository.findById(customerId)
                .map(c -> {
                    log.info(msg("customer.log.found.by.id", c.getCustomerId(), c.getEmail()));
                    return customerMapper.toResponse(c);
                })
                .orElseThrow(() -> new CustomerNotFoundException(
                        msg("customer.not.found.by.id", customerId),
                        "customer.not.found.by.id"));
    }

    /**
     * Returns a customer by their email address.
     *
     * @param email the customer's email
     * @return CustomerResponse DTO
     * @throws CustomerNotFoundException if no customer exists with this email
     */
    @Cacheable(value = CACHE_CUSTOMERS, key = "'email-' + #email")
    public CustomerResponse findByEmail(String email) {
        return customerRepository.findCustomerByEmail(email)
                .map(c -> {
                    log.info(msg("customer.log.found.by.email", email));
                    return customerMapper.toResponse(c);
                })
                .orElseThrow(() -> new CustomerNotFoundException(
                        msg("customer.not.found.by.email", email),
                        "customer.not.found.by.email"));
    }

    // -------------------------------------------------------
    // WRITE
    // -------------------------------------------------------

    /**
     * Creates a new customer.
     *
     * @param request validated customer creation request
     * @return generated customer ID
     * @throws EmailAlreadyExistsException if a customer with this email already exists
     */
    @CacheEvict(value = CACHE_CUSTOMER_LIST, allEntries = true)
    public String createCustomer(CustomerRequest request) {
        try {
            if (customerRepository.findCustomerByEmail(request.email()).isPresent()) {
                log.warn(msg("customer.log.email.duplicate", request.email()));
                throw new EmailAlreadyExistsException(
                        msg("customer.email.already.exists", request.email()),
                        "customer.email.already.exists");
            }
            Customer saved = customerRepository.save(customerMapper.toEntity(request));
            log.info(msg("customer.log.created", saved.getCustomerId(), saved.getEmail()));
            return saved.getCustomerId();
        } catch (EmailAlreadyExistsException ex) {
            throw ex; // re-throw typed exception
        } catch (DataIntegrityViolationException | IncorrectResultSizeDataAccessException ex) {
            throw new EmailAlreadyExistsException(
                    msg("customer.email.already.exists", request.email()),
                    "customer.email.already.exists");
        }
    }

    /**
     * Idempotent customer registration used by auth-service.
     * If a customer with this customerId already exists, returns it.
     * Otherwise creates a new record. Email duplicates do NOT throw —
     * they are treated as the same logical user during cross-service sync.
     */
    @CacheEvict(value = CACHE_CUSTOMER_LIST, allEntries = true)
    public String ensureCustomer(CustomerRequest request) {
        if (request.customerId() != null && customerRepository.existsById(request.customerId())) {
            log.info("[CustomerService] ensureCustomer: already exists id=[{}]", request.customerId());
            return request.customerId();
        }
        try {
            Customer entity = customerMapper.toEntity(request);
            if (request.customerId() != null) {
                entity.setCustomerId(request.customerId());
            }
            Customer saved = customerRepository.save(entity);
            log.info("[CustomerService] ensureCustomer: created id=[{}] email=[{}]",
                    saved.getCustomerId(), saved.getEmail());
            return saved.getCustomerId();
        } catch (DataIntegrityViolationException ex) {
            // Concurrent insert — fall back to existing record by email
            return customerRepository.findCustomerByEmail(request.email())
                    .map(Customer::getCustomerId)
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Updates an existing customer.
     * BUG FIX: CacheEvict uses #customerId (method param) — not #request.customerId()
     * which is nullable.
     *
     * @param customerId the ID of the customer to update
     * @param request    the updated fields
     * @return updated CustomerResponse DTO
     * @throws CustomerNotFoundException if no customer exists with this ID
     */
    @Caching(evict = {
            @CacheEvict(value = CACHE_CUSTOMERS,     key = "#customerId"),
            @CacheEvict(value = CACHE_CUSTOMER_LIST, allEntries = true)
    })
    public CustomerResponse updateCustomer(String customerId, CustomerRequest request) {
        Customer existing = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        msg("customer.not.found.by.id", customerId),
                        "customer.not.found.by.id"));
        mergeFields(existing, request);
        Customer saved = customerRepository.save(existing);
        log.info(msg("customer.log.updated", saved.getCustomerId()));
        return customerMapper.toResponse(saved);
    }

    /**
     * Deletes a customer by ID.
     *
     * @param customerId the ID of the customer to delete
     * @throws CustomerNotFoundException if no customer exists with this ID
     */
    @Caching(evict = {
            @CacheEvict(value = CACHE_CUSTOMERS,     key = "#customerId"),
            @CacheEvict(value = CACHE_CUSTOMER_LIST, allEntries = true)
    })
    public void deleteCustomer(String customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        msg("customer.not.found.by.id", customerId),
                        "customer.not.found.by.id"));
        customerRepository.deleteById(customer.getCustomerId());
        log.info(msg("customer.log.deleted", customerId));
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /**
     * Merges non-blank fields from the request into the existing customer entity.
     * Fields that are null or blank in the request are left unchanged.
     */
    private void mergeFields(Customer customer, CustomerRequest request) {
        if (StringUtils.hasText(request.firstname())) customer.setFirstname(request.firstname());
        if (StringUtils.hasText(request.lastname()))  customer.setLastname(request.lastname());
        if (StringUtils.hasText(request.email()))     customer.setEmail(request.email());
        if (request.address() != null)                customer.setAddress(request.address());
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
