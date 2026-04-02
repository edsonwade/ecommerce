package code.with.vanilson.tenantservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TenantServiceConfig — Infrastructure Layer
 * <p>
 * Configures:
 * - MessageSource backed by messages.properties
 * - JPA Auditing for @CreatedDate / @LastModifiedDate
 * - OpenAPI / Swagger specification
 *
 * @author vamuhong
 * @version 4.0
 */
@Configuration
@EnableJpaAuditing
public class TenantServiceConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Bean
    public OpenAPI tenantServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tenant Service API")
                        .description("SaaS eCommerce — Tenant onboarding, plan management and feature flags")
                        .version("v4.0")
                        .contact(new Contact().name("Vanilson Muhongo").email("vamuhong@ecommerce.io")))
                .servers(List.of(
                        new Server().url("http://localhost:8095").description("Local"),
                        new Server().url("http://gateway:8222/api/v1/tenants").description("Via Gateway")));
    }
}
