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
import code.with.vanilson.productservice.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Verifies that PageImpl<ProductResponse> can round-trip through the same
 * ObjectMapper used by GenericJackson2JsonRedisSerializer in ProductServiceConfig.
 */
class PageImplRedisSerializerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
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
    }

    @Test
    void pageImpl_roundTrip_preservesContentAndPagination() throws Exception {
        List<ProductResponse> products = List.of(
                new ProductResponse(1, "Laptop", "A laptop", 10.0, new BigDecimal("999.99"), 1, "Electronics", "Devices", "admin", null),
                new ProductResponse(2, "Phone", "A phone", 5.0, new BigDecimal("499.99"), 1, "Electronics", "Devices", "admin", null)
        );
        PageImpl<ProductResponse> original = new PageImpl<>(products, PageRequest.of(0, 20), 106);

        String json = mapper.writeValueAsString(original);
        System.out.println("=== Serialized JSON (first 500 chars) ===");
        System.out.println(json.substring(0, Math.min(500, json.length())));

        assertThatNoException()
                .as("Deserialization must not throw")
                .isThrownBy(() -> mapper.readValue(json, PageImpl.class));

        @SuppressWarnings("unchecked")
        PageImpl<ProductResponse> result = mapper.readValue(json, PageImpl.class);

        assertThat(result.getTotalElements()).isEqualTo(106);
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void pageImpl_secondPage_preservesPaginationMetadata() throws Exception {
        List<ProductResponse> products = List.of(
                new ProductResponse(21, "Tablet", "A tablet", 3.0, new BigDecimal("299.99"), 2, "Tech", "Gadgets", "seller", null)
        );
        PageImpl<ProductResponse> page2 = new PageImpl<>(products, PageRequest.of(1, 20), 106);

        String json = mapper.writeValueAsString(page2);

        @SuppressWarnings("unchecked")
        PageImpl<ProductResponse> result = mapper.readValue(json, PageImpl.class);

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(106);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void pageImpl_emptyPage_doesNotThrow() throws Exception {
        PageImpl<ProductResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        String json = mapper.writeValueAsString(emptyPage);

        @SuppressWarnings("unchecked")
        PageImpl<ProductResponse> result = mapper.readValue(json, PageImpl.class);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // Exact copy of the deserializer from ProductServiceConfig
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
                JsonNode itemsArray = (contentNode.size() == 2 && contentNode.get(0).isTextual())
                        ? contentNode.get(1)
                        : contentNode;
                for (JsonNode item : itemsArray) {
                    content.add(mapper.treeToValue(item, code.with.vanilson.productservice.ProductResponse.class));
                }
            }

            int number = node.path("number").asInt(0);
            int size = node.path("size").asInt(20);
            long totalElements = node.path("totalElements").asLong(0);

            return new PageImpl<>(content, PageRequest.of(number, size), totalElements);
        }
    }
}
