package code.with.vanilson.orderservice.integration;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.internal.InternalTokenFilter;
import code.with.vanilson.orderservice.orderLine.OrderLine;
import code.with.vanilson.orderservice.orderLine.OrderLineRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalPurchaseVerificationIntegrationTest — F7 Task 7.1 end-to-end against real PostgreSQL.
 * <p>
 * Proves two things the mock/slice layers cannot: (1) the {@code existsPurchasedProduct} JPQL runs
 * correctly on real Postgres — true only for a fulfilled order (CONFIRMED/SHIPPED/DELIVERED), false
 * for a wrong product or an unfulfilled order; (2) the {@link InternalTokenFilter} shared-secret
 * guard is wired into the real filter chain — a valid {@code X-Internal-Token} reaches the endpoint,
 * a missing/wrong one is rejected with 401 before the controller.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=order-internal-verify-test",
                "application.security.internal-token=test-internal-token"
        })
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"order-topic"},
        brokerProperties = {
                "auto.create.topics.enable=true",
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1",
                "log.index.size.max.bytes=1048576"
        })
@ActiveProfiles("test")
@DisplayName("Internal Purchase Verification Integration — F7 (Testcontainers PostgreSQL)")
class InternalPurchaseVerificationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_internal_verify_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String TENANT = "test-tenant";
    private static final String TOKEN = "test-internal-token";
    private static final String EXISTS_PATH = "/api/v1/orders/internal/purchases/exists";

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderLineRepository orderLineRepository;

    @MockBean CustomerClient customerClient;

    @BeforeEach
    void seed() {
        TenantContext.setCurrentTenantId(TENANT);
        // Customer 42 has a CONFIRMED order with a line for product 1 (a real, fulfilled purchase).
        persistOrder("42", 1, OrderStatus.CONFIRMED);
        // Customer 55 has only a REQUESTED (not-yet-fulfilled) order for product 2.
        persistOrder("55", 2, OrderStatus.REQUESTED);
    }

    @AfterEach
    void cleanup() {
        orderLineRepository.deleteAll();
        orderRepository.deleteAll();
        TenantContext.clear();
    }

    private void persistOrder(String customerId, int productId, OrderStatus status) {
        Order order = Order.builder()
                .tenantId(TENANT)
                .correlationId(UUID.randomUUID().toString())
                .reference("ORD-" + UUID.randomUUID().toString().substring(0, 8))
                .totalAmount(BigDecimal.valueOf(99.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId(customerId)
                .status(status)
                .build();
        Order saved = orderRepository.save(order);
        orderLineRepository.save(OrderLine.builder()
                .tenantId(TENANT)
                .order(saved)
                .productId(productId)
                .quantity(1.0)
                .build());
    }

    @Test
    @DisplayName("valid token + fulfilled purchase → 200 {\"purchased\":true}")
    void fulfilledPurchaseReturnsTrue() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "42")
                        .param("productId", "1")
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN)
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(true));
    }

    @Test
    @DisplayName("valid token + product never bought → 200 {\"purchased\":false}")
    void neverBoughtReturnsFalse() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "42")
                        .param("productId", "999")
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN)
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(false));
    }

    @Test
    @DisplayName("valid token + only an unfulfilled (REQUESTED) order → 200 {\"purchased\":false}")
    void unfulfilledOrderReturnsFalse() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "55")
                        .param("productId", "2")
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN)
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(false));
    }

    @Test
    @DisplayName("missing X-Internal-Token → 401 before the controller")
    void missingTokenRejected() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "42")
                        .param("productId", "1")
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("order.internal.token.invalid"));
    }

    @Test
    @DisplayName("wrong X-Internal-Token → 401")
    void wrongTokenRejected() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "42")
                        .param("productId", "1")
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, "wrong-token")
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized());
    }
}
