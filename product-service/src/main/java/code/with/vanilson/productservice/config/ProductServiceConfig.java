package code.with.vanilson.productservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
 * @version 2.2
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
        // ProductResponse is a record (final). GenericJackson2JsonRedisSerializer with NON_FINAL
        // typing never writes @class for final types, so deserialization via readValue(bytes, Object.class)
        // fails with "missing type id property '@class'". Use a statically-typed serializer instead.
        var productSerializer = new Jackson2JsonRedisSerializer<>(buildBaseObjectMapper(),
                code.with.vanilson.productservice.ProductResponse.class);
        RedisCacheConfiguration singleConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(productSerializer));

        // PageImpl is non-final — the generic serializer (with activateDefaultTyping) works correctly
        // here, aided by the custom PageImplDeserializer registered below.
        RedisCacheConfiguration listConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                buildRedisSerializer()));

        return RedisCacheManager.builder(factory)
                .withCacheConfiguration(CACHE_PRODUCTS, singleConfig)
                .withCacheConfiguration(CACHE_PRODUCT_LIST, listConfig)
                .build();
    }

    @Bean
    public InitializingBean flushStaleProductCache(StringRedisTemplate redisTemplate) {
        return () -> {
            var keys = redisTemplate.keys("product*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        };
    }

    static final String CACHE_PRODUCTS = "products";
    static final String CACHE_PRODUCT_LIST = "product-list";

    private ObjectMapper buildBaseObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private GenericJackson2JsonRedisSerializer buildRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        SimpleModule pageModule = new SimpleModule("page-impl-module");
        pageModule.addDeserializer(PageImpl.class, new PageImplDeserializer());
        mapper.registerModule(pageModule);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /**
     * Custom deserializer for PageImpl that reconstructs pagination from simple primitives
     * (number, size, totalElements) instead of trying to deserialize the stored PageRequest/Sort
     * objects — which have no default constructors and break Jackson's default typing.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static class PageImplDeserializer extends StdDeserializer<PageImpl> {

        PageImplDeserializer() {
            super(PageImpl.class);
        }

        @Override
        public PageImpl deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            ObjectMapper mapper = (ObjectMapper) p.getCodec();
            JsonNode node = mapper.readTree(p);

            List<Object> content = new ArrayList<>();
            JsonNode contentNode = node.get("content");
            if (contentNode != null && contentNode.isArray()) {
                // activateDefaultTyping wraps List as ["java.util.ArrayList", [items...]]
                // Detect and unwrap: first element is the class name string, second is the actual array
                JsonNode itemsArray = (contentNode.size() == 2 && contentNode.get(0).isTextual())
                        ? contentNode.get(1)
                        : contentNode;
                for (JsonNode item : itemsArray) {
                    // ProductResponse is a record (final) — no @class added, deserialize by type directly
                    content.add(mapper.treeToValue(item, code.with.vanilson.productservice.ProductResponse.class));
                }
            }

            int number = node.path("number").asInt(0);
            int size = node.path("size").asInt(20);
            long totalElements = node.path("totalElements").asLong(0);

            return new PageImpl<>(content, PageRequest.of(number, size), totalElements);
        }
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
