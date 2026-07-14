package code.with.vanilson.orderservice.integration;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.OrderResponse;
import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderIllegalStateTransitionException;
import code.with.vanilson.orderservice.orderLine.OrderLine;
import code.with.vanilson.orderservice.orderLine.OrderLineRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderFulfillmentIntegrationTest — Fase 5 manual fulfillment against real PostgreSQL.
 * <p>
 * Proves the {@code V1_14} migration end-to-end: an order advanced to SHIPPED/DELIVERED
 * round-trips through the {@code shipped_at} / {@code delivered_at} columns, the seller
 * line-ownership query authorises a seller who owns a line and rejects one who does not, and
 * the transition guard is enforced on real persisted state.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=order-fulfillment-group-test"
})
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
@DisplayName("Order Fulfillment Integration — Fase 5 (Testcontainers PostgreSQL)")
class OrderFulfillmentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_fulfillment_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String TENANT = "test-tenant";

    @Autowired OrderService        orderService;
    @Autowired OrderRepository     orderRepository;
    @Autowired OrderLineRepository orderLineRepository;

    @MockBean CustomerClient customerClient;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        orderLineRepository.deleteAll();
        orderRepository.deleteAll();
        TenantContext.clear();
    }

    private Order persistConfirmedOrder(String sellerId) {
        Order order = Order.builder()
                .tenantId(TENANT)
                .correlationId(UUID.randomUUID().toString())
                .reference("ORD-" + UUID.randomUUID().toString().substring(0, 8))
                .totalAmount(BigDecimal.valueOf(120.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId("42")
                .status(OrderStatus.CONFIRMED)
                .build();
        Order saved = orderRepository.save(order);

        OrderLine line = OrderLine.builder()
                .tenantId(TENANT)
                .order(saved)
                .productId(1)
                .quantity(2.0)
                .sellerId(sellerId)
                .build();
        orderLineRepository.save(line);
        return saved;
    }

    private void authenticateAs(long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user@test.com", userId, TENANT, role), null, List.of()));
    }

    @Test
    @DisplayName("ADMIN ships then delivers — timestamps persist through V1_14 columns")
    void adminShipThenDeliverPersistsTimestamps() {
        Order order = persistConfirmedOrder("7");
        authenticateAs(1L, "ADMIN");

        OrderResponse shipped = orderService.updateStatusManually(order.getOrderId(), OrderStatus.SHIPPED);
        assertThat(shipped.status()).isEqualTo("SHIPPED");

        Order afterShip = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertThat(afterShip.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(afterShip.getShippedAt()).isNotNull();
        assertThat(afterShip.getDeliveredAt()).isNull();

        orderService.updateStatusManually(order.getOrderId(), OrderStatus.DELIVERED);
        Order afterDeliver = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertThat(afterDeliver.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(afterDeliver.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("SELLER owning a line may ship the order")
    void sellerOwningLineMayShip() {
        Order order = persistConfirmedOrder("7");
        authenticateAs(7L, "SELLER");

        OrderResponse shipped = orderService.updateStatusManually(order.getOrderId(), OrderStatus.SHIPPED);

        assertThat(shipped.status()).isEqualTo("SHIPPED");
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("SELLER without a line in the order is forbidden")
    void foreignSellerForbidden() {
        Order order = persistConfirmedOrder("7");
        authenticateAs(99L, "SELLER"); // owns no line

        assertThatThrownBy(() -> orderService.updateStatusManually(order.getOrderId(), OrderStatus.SHIPPED))
                .isInstanceOf(OrderForbiddenException.class);

        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("illegal transition (DELIVERED before SHIPPED) is rejected on persisted state")
    void illegalTransitionRejected() {
        Order order = persistConfirmedOrder("7");
        authenticateAs(1L, "ADMIN");

        assertThatThrownBy(() -> orderService.updateStatusManually(order.getOrderId(), OrderStatus.DELIVERED))
                .isInstanceOf(OrderIllegalStateTransitionException.class);
    }
}
