package code.with.vanilson.cartservice;

import code.with.vanilson.tenantcontext.EnableMultiTenancy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * CartServiceApplication — Entry Point
 * <p>
 * Bootstraps the Cart Service.
 * @EnableDiscoveryClient → registers with Eureka (lb://CART-SERVICE in Gateway)
 * @EnableMultiTenancy    → Phase 4: activates tenant context + Feign propagation.
 *                          Hibernate filter is NOT activated (no JPA on classpath) —
 *                          tenant isolation uses Redis key prefix instead.
 * @EnableRedisRepositories is activated by CartServiceConfig.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableMultiTenancy
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
