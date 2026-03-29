package code.with.vanilson.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OrderServiceApplication — Entry Point (Phase 3 update)
 * <p>
 * @EnableScheduling activates the OutboxEventPublisher @Scheduled(fixedDelay=5000).
 * Without this annotation the outbox polling job never runs and no events are published.
 * <p>
 * @EnableFeignClients  — CustomerClient, PaymentClient Feign interfaces
 * @EnableJpaAuditing   — @CreatedDate / @LastModifiedDate on Order + OutboxEvent
 * @EnableDiscoveryClient — registers with Eureka
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@EnableJpaAuditing
@EnableFeignClients
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
