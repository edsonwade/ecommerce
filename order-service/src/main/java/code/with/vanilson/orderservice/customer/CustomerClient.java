package code.with.vanilson.orderservice.customer;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

/**
 * CustomerClient
 * <p>
 * Feign client owned by order-service.
 * Communicates with customer-service via the API Gateway (lb://CUSTOMER-SERVICE).
 * Returns order-service's own CustomerInfo DTO — no shared JAR dependency.
 * <p>
 * Circuit breaker: customer-cb (configured in order-service.yml via Resilience4j).
 * On open circuit → CustomerServiceUnavailableException thrown by fallback.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@FeignClient(
        name = "customer-service",
        url = "${application.config.customer-url}",
        fallbackFactory = CustomerClientFallbackFactory.class
)
public interface CustomerClient {

    @GetMapping("/{customer-id}")
    Optional<CustomerInfo> findCustomerById(@PathVariable("customer-id") String customerId);
}
