package net.javaguides.employeeservice.config;

import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class FeignConfiguration {

    @Bean
    @Primary
    public FeignClientProperties feignClientProperties() {
        return new FeignClientProperties();
    }
}
