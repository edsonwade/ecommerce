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
import code.with.vanilson.tenantcontext.internal.InternalTokenAuthenticationFilter;
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
 * InternalPurchaseRequireCallerIntegrationTest — composition-layer proof for {@code require-caller-header:true}.
 * <p>
 * This is the layer the hardening pass had left uncovered. The rule that a valid token with no
 * {@code X-Internal-Caller} is rejected was proven twice, but never in composition:
 * <ul>
 *   <li>properties → authenticator, in {@code InternalTokenAutoConfigurationTest#requireCallerHeaderIsHonoured};</li>
 *   <li>authenticator → 401/200, in {@code InternalTokenFilterTest} (impersonation and friends).</li>
 * </ul>
 * Here the enforcement is exercised end-to-end through the <em>real</em> order-service filter chain:
 * {@code application.security.internal.accepted} is bound from properties, the
 * {@link code.with.vanilson.tenantcontext.internal.InternalTokenAutoConfiguration} builds the
 * per-caller authenticator, {@code OrderSecurityConfig} wires it into {@link InternalTokenFilter}, and
 * MockMvc drives it over the same servlet chain a real product-service call would traverse.
 * <p>
 * Sibling of {@link InternalPurchaseVerificationIntegrationTest}, which covers the legacy single-secret
 * ({@code require-caller-header=false}) configuration. The two together pin both sides of the migration:
 * an un-migrated deployment (there) and a fully-migrated, identity-enforcing one (here).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=order-internal-require-caller-test",
                // Fully-migrated configuration: per-caller secret + identity enforced.
                // No legacy application.security.internal-token here — once a per-caller secret exists
                // the auto-config ignores the legacy value, so leaving it out mirrors production.
                "application.security.internal.accepted.product-service[0]=require-caller-token",
                "application.security.internal.require-caller-header=true"
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
@DisplayName("Internal Purchase — require-caller-header enforced through the real chain (Testcontainers PostgreSQL)")
class InternalPurchaseRequireCallerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_internal_require_caller_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String TENANT = "test-tenant";
    private static final String TOKEN = "require-caller-token";
    private static final String CALLER = "product-service";
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
    @DisplayName("valid token + matching X-Internal-Caller → 200 {\"purchased\":true}")
    void validTokenAndCallerReaches200() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "42")
                        .param("productId", "1")
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN)
                        .header(InternalTokenAuthenticationFilter.CALLER_HEADER, CALLER)
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(true));
    }

    @Test
    @DisplayName("valid token but NO X-Internal-Caller → 401 (this is what require-caller-header enforces)")
    void validTokenWithoutCallerRejected() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "42")
                        .param("productId", "1")
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN)
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("order.internal.token.invalid"));
    }

    @Test
    @DisplayName("valid token under someone else's name → 401 (impersonation) through the real chain")
    void impersonationRejectedThroughChain() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "42")
                        .param("productId", "1")
                        .header(InternalTokenFilter.INTERNAL_TOKEN_HEADER, TOKEN)
                        .header(InternalTokenAuthenticationFilter.CALLER_HEADER, "cart-service")
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("missing token → 401 regardless of caller header")
    void missingTokenRejected() throws Exception {
        mockMvc.perform(get(EXISTS_PATH)
                        .param("customerId", "42")
                        .param("productId", "1")
                        .header(InternalTokenAuthenticationFilter.CALLER_HEADER, CALLER)
                        .header(TenantContext.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized());
    }
}
