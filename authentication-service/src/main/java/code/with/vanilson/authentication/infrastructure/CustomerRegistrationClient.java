package code.with.vanilson.authentication.infrastructure;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "customer-registration-client",
        url = "${application.config.customer-url}"
)
public interface CustomerRegistrationClient {

    // Circuit breaker: customer-registration (configured in authentication-service.yml).
    // When open, CallNotPermittedException is thrown immediately — callers treat the
    // backfill as fail-open (login proceeds), so no fallback method is needed.
    @CircuitBreaker(name = "customer-registration")
    @PostMapping("/internal")
    void createCustomer(@RequestBody CustomerRegistrationRequest request);

    @CircuitBreaker(name = "customer-registration")
    @PutMapping("/internal/{customerId}")
    void updateCustomer(@PathVariable("customerId") String customerId,
                        @RequestBody CustomerRegistrationRequest request);

    @CircuitBreaker(name = "customer-registration")
    @DeleteMapping("/internal/{customerId}")
    void deleteCustomer(@PathVariable("customerId") String customerId);

    record CustomerRegistrationRequest(
            String customerId,
            String firstname,
            String lastname,
            String email
    ) {}
}
