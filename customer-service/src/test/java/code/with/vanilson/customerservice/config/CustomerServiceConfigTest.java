package code.with.vanilson.customerservice.config;

import code.with.vanilson.customerservice.Address;
import code.with.vanilson.customerservice.CustomerResponse;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CustomerServiceConfigTest — Unit Tests
 * <p>
 * Validates that the Redis serializer correctly handles Java records (final classes)
 * and that the CacheErrorHandler is configured.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@DisplayName("CustomerServiceConfig — Unit Tests")
class CustomerServiceConfigTest {

    private GenericJackson2JsonRedisSerializer serializer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );
        serializer = new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Nested
    @DisplayName("Redis serializer — Java record round-trip")
    class RedisSerializerRoundTrip {

        @Test
        @DisplayName("should serialize and deserialize CustomerResponse record with @class type info")
        void shouldRoundTripCustomerResponseRecord() {
            Address address = new Address("Main St", "42", "10001", "Portugal", "Lisbon");
            CustomerResponse original = new CustomerResponse(
                    "cust-001", "Ana", "Silva", "ana@example.com", address);

            byte[] serialized = serializer.serialize(original);
            assertThat(serialized).isNotNull();

            String json = new String(serialized);
            assertThat(json).contains("@class");
            assertThat(json).contains("CustomerResponse");

            Object deserialized = serializer.deserialize(serialized);
            assertThat(deserialized)
                    .isNotNull()
                    .isInstanceOf(CustomerResponse.class);

            CustomerResponse result = (CustomerResponse) deserialized;
            assertThat(result.customerId()).isEqualTo("cust-001");
            assertThat(result.firstname()).isEqualTo("Ana");
            assertThat(result.email()).isEqualTo("ana@example.com");
            assertThat(result.address()).isNotNull();
            assertThat(result.address().getCity()).isEqualTo("Lisbon");
        }

        @Test
        @DisplayName("should serialize and deserialize CustomerResponse with null address")
        void shouldRoundTripWithNullAddress() {
            CustomerResponse original = new CustomerResponse(
                    "cust-002", "Bruno", "Costa", "bruno@example.com", null);

            byte[] serialized = serializer.serialize(original);
            Object deserialized = serializer.deserialize(serialized);

            assertThat(deserialized).isInstanceOf(CustomerResponse.class);
            CustomerResponse result = (CustomerResponse) deserialized;
            assertThat(result.customerId()).isEqualTo("cust-002");
            assertThat(result.address()).isNull();
        }

        @Test
        @DisplayName("serialized JSON must contain @class for record types (EVERYTHING typing)")
        void serializedJsonMustContainClassForRecords() {
            CustomerResponse original = new CustomerResponse(
                    "cust-003", "Carlos", "Lima", "carlos@example.com", null);

            byte[] serialized = serializer.serialize(original);
            String json = new String(serialized);

            assertThat(json)
                    .as("EVERYTHING typing must add @class even for final record types")
                    .contains("@class");
        }
    }

    @Nested
    @DisplayName("CacheErrorHandler")
    class CacheErrorHandlerTest {

        @Test
        @DisplayName("should return a non-null CacheErrorHandler that logs instead of throwing")
        void shouldReturnNonNullErrorHandler() {
            CustomerServiceConfig config = new CustomerServiceConfig();
            CacheErrorHandler handler = config.errorHandler();

            assertThat(handler).isNotNull();

            Cache mockCache = mock(Cache.class);
            when(mockCache.getName()).thenReturn("customers");

            assertThatCode(() -> handler.handleCacheGetError(
                    new RuntimeException("deserialization error"), mockCache, "cust-001"))
                    .doesNotThrowAnyException();

            assertThatCode(() -> handler.handleCachePutError(
                    new RuntimeException("serialization error"), mockCache, "cust-001", "value"))
                    .doesNotThrowAnyException();

            assertThatCode(() -> handler.handleCacheEvictError(
                    new RuntimeException("evict error"), mockCache, "cust-001"))
                    .doesNotThrowAnyException();

            assertThatCode(() -> handler.handleCacheClearError(
                    new RuntimeException("clear error"), mockCache))
                    .doesNotThrowAnyException();
        }
    }
}
