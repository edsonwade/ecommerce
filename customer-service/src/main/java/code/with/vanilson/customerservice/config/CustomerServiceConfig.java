package code.with.vanilson.customerservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * CustomerServiceConfig — Infrastructure Layer
 * <p>
 * Configures:
 * - MessageSource backed by messages.properties
 * - Redis L2 cache (@EnableCaching)
 * - OpenAPI / Swagger self-documenting API spec
 * <p>
 * MongoDB sharding note:
 * Customer data is sharded by customerId (hashed shard key) in production MongoDB Atlas.
 * This distributes customers evenly across shards and supports horizontal scaling.
 * Index on email field is defined in the Customer @Document class with @Indexed(unique=true).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Configuration
@EnableCaching
public class CustomerServiceConfig {

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
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                buildRedisSerializer()));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }

    private GenericJackson2JsonRedisSerializer buildRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public InitializingBean flushStaleCustomerCache(StringRedisTemplate redisTemplate) {
        return () -> {
            var keys = redisTemplate.keys("customer*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        };
    }

    @Bean
    public OpenAPI customerServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Customer Service API")
                        .description("SaaS eCommerce — Customer profile management")
                        .version("v2.0")
                        .contact(new Contact().name("Vanilson Muhongo").email("vamuhong@ecommerce.io")))
                .servers(List.of(
                        new Server().url("http://localhost:8090").description("Local"),
                        new Server().url("http://gateway:8222/api/v1/customers").description("Via Gateway")));
    }
}
