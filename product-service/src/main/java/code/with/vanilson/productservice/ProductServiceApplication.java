package code.with.vanilson.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * ProductServiceApplication — Entry Point (Phase 3 update)
 * <p>
 * @EnableDiscoveryClient  — registers with Eureka (required for lb:// routing)
 * @EnableCaching          — activates @Cacheable / @CacheEvict in ProductService
 * @EnableJpaAuditing      — @CreatedDate / @LastModifiedDate on Product entity
 * <p>
 * Phase 3: InventoryReservationConsumer is activated automatically
 * via @KafkaListener — no explicit annotation needed here.
 * ProductKafkaConfig declares the consumer factory.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
