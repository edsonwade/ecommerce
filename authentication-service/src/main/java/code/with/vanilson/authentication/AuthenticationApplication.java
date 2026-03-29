package code.with.vanilson.authentication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AuthenticationApplication — Entry Point
 * <p>
 * Bootstraps the Authentication Service.
 * @EnableDiscoveryClient  → registers with Eureka
 * @EnableAsync            → enables @Async on email/notification tasks
 * JPA Auditing is activated by SecurityConfig @EnableJpaAuditing.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class AuthenticationApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthenticationApplication.class, args);
    }
}
