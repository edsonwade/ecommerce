package code.with.vanilson.authentication.infrastructure;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
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

    record CustomerRegistrationRequest(
            String customerId,
            String firstname,
            String lastname,
            String email
    ) {}
}
