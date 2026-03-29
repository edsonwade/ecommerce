package code.with.vanilson.paymentservice.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.nio.charset.StandardCharsets;

/**
 * AppConfig — Infrastructure Layer
 * <p>
 * Central Spring configuration for Payment Service.
 * Dependency Inversion (SOLID-D): all high-level modules depend on
 * abstractions (MessageSource interface) provided here.
 * <p>
 * Responsibilities:
 * - Expose MessageSource backed by messages.properties
 * - Enable JPA auditing (@CreatedDate, @LastModifiedDate on Payment entity)
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Configuration
@EnableJpaAuditing
public class AppConfig {

    /**
     * MessageSource backed by messages.properties.
     * All exception and log messages must be resolved through this bean.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }
}
