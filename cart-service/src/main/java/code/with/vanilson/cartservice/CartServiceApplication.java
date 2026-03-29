package code.with.vanilson.cartservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * CartServiceApplication — Entry Point
 * <p>
 * Bootstraps the Cart Service.
 * @EnableDiscoveryClient → registers with Eureka (lb://CART-SERVICE in Gateway)
 * @EnableRedisRepositories is activated by CartServiceConfig.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@SpringBootApplication
@EnableDiscoveryClient
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
