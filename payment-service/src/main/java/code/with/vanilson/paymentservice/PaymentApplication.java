package code.with.vanilson.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * PaymentApplication — Entry Point (Phase 3 update)
 * <p>
 * @EnableDiscoveryClient — registers with Eureka.
 * Fixed: removed deprecated @EnableEurekaClient → @EnableDiscoveryClient.
 * AppConfig provides @EnableJpaAuditing — not repeated here (Single Responsibility).
 * <p>
 * Phase 3: PaymentSagaConsumer is auto-activated via @KafkaListener.
 * PaymentKafkaConfig declares the saga consumer factory.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
