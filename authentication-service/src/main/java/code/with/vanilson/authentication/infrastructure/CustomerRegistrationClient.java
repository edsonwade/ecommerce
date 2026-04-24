package code.with.vanilson.authentication.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "customer-registration-client",
        url = "${application.config.customer-url}"
)
public interface CustomerRegistrationClient {

    @PostMapping
    void createCustomer(@RequestBody CustomerRegistrationRequest request);

    record CustomerRegistrationRequest(
            String customerId,
            String firstname,
            String lastname,
            String email
    ) {}
}
