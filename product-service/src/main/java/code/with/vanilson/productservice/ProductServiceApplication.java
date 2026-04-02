package code.with.vanilson.productservice;

import code.with.vanilson.tenantcontext.EnableMultiTenancy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * ProductServiceApplication — Entry Point (Phase 3 update)
 * <p>
 * @EnableDiscoveryClient  — registers with Eureka (required for lb:// routing)
 * @EnableCaching          — activates @Cacheable / @CacheEvict in ProductService
 * @EnableMultiTenancy     — Phase 4: activates tenant context, Hibernate filter, Feign propagation
 * <p>
 * Phase 3: InventoryReservationConsumer is activated automatically
 * via @KafkaListener — no explicit annotation needed here.
 * ProductKafkaConfig declares the consumer factory.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableMultiTenancy
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
