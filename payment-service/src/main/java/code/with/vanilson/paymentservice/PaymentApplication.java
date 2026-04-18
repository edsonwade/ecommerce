package code.with.vanilson.paymentservice;

import code.with.vanilson.tenantcontext.EnableMultiTenancy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * PaymentApplication — Entry Point (Phase 3 update)
 * <p>
 * @EnableDiscoveryClient — registers with Eureka.
 * @EnableMultiTenancy    — Phase 4: activates tenant context, Hibernate filter, Feign propagation.
 * <p>
 * AppConfig provides @EnableJpaAuditing — not repeated here (Single Responsibility).
 * Phase 3: PaymentSagaConsumer is auto-activated via @KafkaListener.
 * PaymentKafkaConfig declares the saga consumer factory.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableDiscoveryClient
@EnableMultiTenancy
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
