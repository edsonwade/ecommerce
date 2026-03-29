package code.with.vanilson.customerservice;

import code.with.vanilson.customerservice.exception.CustomerNotFoundException;
import code.with.vanilson.customerservice.exception.EmailAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * CustomerService — Application Layer
 * <p>
 * Core business logic for customer management.
 * <p>
 * KEY CHANGES FROM ORIGINAL:
 * 1. All hardcoded exception messages → MessageSource resolution from messages.properties.
 * 2. Two lists merged into one: findAllCustomers() returns CustomerResponse (was split confusingly).
 * 3. Redis L2 cache on reads — customers are relatively static, safe to cache 10 min.
 * 4. Removed MongoTemplate constructor parameter (unused — deleted from constructor).
 * 5. StringUtils.hasText() replaces deprecated Commons Lang StringUtils.isNotBlank().
 * 6. Single Responsibility: each method does one thing.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class CustomerService {

    private static final String CACHE_CUSTOMERS = "customers";
    private static final String CACHE_CUSTOMER_LIST = "customer-list";

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final MessageSource messageSource;

    public CustomerService(CustomerMapper customerMapper,
                           CustomerRepository customerRepository,
                           MessageSource messageSource) {
        this.customerMapper = customerMapper;
        this.customerRepository = customerRepository;
        this.messageSource = messageSource;
    }

    // -------------------------------------------------------
    // READ
    // -------------------------------------------------------

    @Cacheable(CACHE_CUSTOMER_LIST)
    public List<CustomerResponse> findAllCustomers() {
        List<CustomerResponse> customers = customerRepository.findAll()
                .stream()
                .map(customerMapper::fromCustomer)
                .toList();
        log.info(msg("customer.log.all.found", customers.size()));
        return customers;
    }

    @Cacheable(value = CACHE_CUSTOMERS, key = "#customerId")
    public CustomerResponse getCustomerById(String customerId) {
        return customerRepository.findById(customerId)
                .map(c -> {
                    log.info(msg("customer.log.found.by.id", c.getCustomerId(), c.getEmail()));
                    return customerMapper.fromCustomer(c);
                })
                .orElseThrow(() -> new CustomerNotFoundException(
                        msg("customer.not.found.by.id", customerId),
                        "customer.not.found.by.id"));
    }

    @Cacheable(value = CACHE_CUSTOMERS, key = "'email-' + #email")
    public CustomerResponse findByEmail(String email) {
        return customerRepository.findCustomerByEmail(email)
                .map(c -> {
                    log.info(msg("customer.log.found.by.email", email));
                    return customerMapper.fromCustomer(c);
                })
                .orElseThrow(() -> new CustomerNotFoundException(
                        msg("customer.not.found.by.email", email),
                        "customer.not.found.by.email"));
    }

    // -------------------------------------------------------
    // WRITE
    // -------------------------------------------------------

    @CacheEvict(value = CACHE_CUSTOMER_LIST, allEntries = true)
    public String createCustomer(CustomerRequest request) {
        try {
            if (customerRepository.findCustomerByEmail(request.email()).isPresent()) {
                log.warn(msg("customer.log.email.duplicate", request.email()));
                throw new EmailAlreadyExistsException(
                        msg("customer.email.already.exists", request.email()),
                        "customer.email.already.exists");
            }
            Customer saved = customerRepository.save(customerMapper.toCustomer(request));
            log.info(msg("customer.log.created", saved.getCustomerId(), saved.getEmail()));
            return saved.getCustomerId();
        } catch (IncorrectResultSizeDataAccessException ex) {
            throw new EmailAlreadyExistsException(
                    msg("customer.email.already.exists", request.email()),
                    "customer.email.already.exists");
        }
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_CUSTOMERS, key = "#request.customerId()"),
            @CacheEvict(value = CACHE_CUSTOMER_LIST, allEntries = true)
    })
    public CustomerResponse updateCustomer(String customerId, CustomerRequest request) {
        var existing = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        msg("customer.not.found.by.id", request.customerId()),
                        "customer.not.found.by.id"));
        mergeFields(existing, request);
        customerRepository.save(existing);
        log.info(msg("customer.log.updated", existing.getCustomerId()));
        return customerMapper.fromCustomer(existing);
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_CUSTOMERS, key = "#customerId"),
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

    private void mergeFields(Customer customer, CustomerRequest request) {
        if (StringUtils.hasText(request.firstname())) customer.setFirstname(request.firstname());
        if (StringUtils.hasText(request.lastname())) customer.setLastname(request.lastname());
        if (StringUtils.hasText(request.email())) customer.setEmail(request.email());
        if (request.address() != null) customer.setAddress(request.address());
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
