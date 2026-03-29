package code.with.vanilson.cartservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CartServiceConfig — Infrastructure Layer
 * <p>
 * Configures:
 * - MessageSource backed by messages.properties
 * - RedisTemplate with JSON serialisation (not default Java serialisation)
 * - @EnableRedisRepositories — activates Spring Data Redis repositories
 * - OpenAPI / Swagger specification
 * <p>
 * Redis serialisation strategy:
 * - Key serialiser:   StringRedisSerializer  → human-readable keys in Redis
 * - Value serialiser: GenericJackson2JsonRedisSerializer → JSON values
 * This allows inspection of cart data with redis-cli without binary noise.
 * <p>
 * Sharding note (production):
 * In production, Redis Cluster with consistent hashing distributes cart keys
 * across 6 nodes (3 masters + 3 replicas). Cart key "cart:{customerId}" acts
 * as the hash slot key — all operations for the same customer hit the same shard.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Configuration
@EnableCaching
@EnableRedisRepositories(basePackages = "code.with.vanilson.cartservice.infrastructure")
public class CartServiceConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    /**
     * RedisTemplate configured with JSON serialisation.
     * Used for manual Redis operations beyond what CartRepository provides.
     * Keys are stored as Strings; values are stored as JSON.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public OpenAPI cartServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cart Service API")
                        .description("SaaS eCommerce — Shopping cart management (Redis-native, 24h TTL)")
                        .version("v2.0")
                        .contact(new Contact().name("Vanilson Muhongo").email("vamuhong@ecommerce.io")))
                .servers(List.of(
                        new Server().url("http://localhost:8091").description("Local"),
                        new Server().url("http://gateway:8222/api/v1/carts").description("Via Gateway")));
    }
}
