package code.with.vanilson.authentication.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

/**
 * MessageSourceConfig — isolated @Configuration for the MessageSource bean.
 * <p>
 * Extracted from SecurityConfig to break the circular dependency:
 *   SecurityConfig → JwtAuthFilter → JwtService → MessageSource
 *                                                       ↑
 *                                          (was defined inside SecurityConfig)
 * <p>
 * By living in its own @Configuration, MessageSource is created before any
 * of the security beans attempt to initialise, so no circular reference exists.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }
}
