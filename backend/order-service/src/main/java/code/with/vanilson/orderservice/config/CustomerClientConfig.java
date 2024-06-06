package code.with.vanilson.orderservice.config;

import code.with.vanilson.customerservice.CustomerClient;
import code.with.vanilson.customerservice.CustomerResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class CustomerClientConfig {
    @Bean
    public CustomerClient customerClient() {
        return customerId -> Optional.empty();
    }
}
