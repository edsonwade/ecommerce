package code.with.vanilson.productservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;


/**
 * ProductServiceConfig — Infrastructure Layer
 * <p>
 * Central configuration for Product Service:
 * - MessageSource: messages.properties resolution
 * - Redis L2 Cache: @EnableCaching activates @Cacheable / @CacheEvict
 * - Spring Data Web: @EnableSpringDataWebSupport for Page serialization via DTO
 * - OpenAPI / Swagger: self-documenting API spec
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Configuration
@EnableCaching
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EnableJpaAuditing
public class ProductServiceConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * OpenAPI specification for the Product Service.
     * Accessible at <a href="http://localhost">...</a>:{port}/swagger-ui.html
     */
    @Bean
    public OpenAPI productServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .description("SaaS eCommerce — Product catalog, inventory management and stock reservation")
                        .version("v2.0")
                        .contact(new Contact()
                                .name("Vanilson Muhongo")
                                .email("vamuhong@ecommerce.io"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Local"),
                        new Server().url("http://gateway:8222/api/v1/products").description("Via Gateway")));
    }
}
