package code.with.vanilson.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import code.with.vanilson.tenantcontext.EnableMultiTenancy;

/**
 * OrderServiceApplication — Entry Point (Phase 4 update)
 * <p>
 * @EnableMultiTenancy — activates tenant context extraction, Hibernate filtering,
 *   and Feign propagation from the tenant-context shared library.
 * @EnableScheduling activates the OutboxEventPublisher @Scheduled(fixedDelay=5000).
 * @EnableFeignClients  — CustomerClient, PaymentClient Feign interfaces
 * @EnableJpaAuditing   — @CreatedDate / @LastModifiedDate on Order + OutboxEvent
 * @EnableDiscoveryClient — registers with Eureka
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@EnableJpaAuditing
@EnableFeignClients
@EnableScheduling
@EnableDiscoveryClient
@EnableMultiTenancy
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
