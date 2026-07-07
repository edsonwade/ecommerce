package code.with.vanilson.orderservice.integration;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.OrderResponse;
import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatusResponse;
import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderTenantIsolationIntegrationTest — B3 Fase 1 (cross-tenant isolation, tests first)
 * <p>
 * Seeds orders under TWO different tenants in a real PostgreSQL (Testcontainers,
 * never H2) and asserts that every read path scoped to one tenant returns ZERO rows
 * of the other. All live users share {@code tenantId="default"}, so this isolation
 * has never been exercised before.
 * <p>
 * Expected outcome per read path (evidence-based, from the current code):
 * <ul>
 *   <li>{@code findAllOrders()} / {@code findMyOrders()} — call
 *       {@code filterActivator.activateFilter()} and query via JPQL, so the Hibernate
 *       tenant filter applies. Expected GREEN.</li>
 *   <li>{@code getOrderStatus()} — queries {@code findByCorrelationIdAndTenantId}
 *       (explicitly tenant-keyed). Expected GREEN.</li>
 *   <li>{@code findById()} — uses {@code repository.findById} (= {@code em.find}),
 *       which Hibernate filters DO NOT apply to by design, and the ownership guard
 *       checks the principal, not the tenant. Expected RED today: tenant A can read
 *       tenant B's order by id. A red run here is the documented cross-tenant leak
 *       that B3 Fase 1b must fix.</li>
 * </ul>
 * <p>
 * Reads run inside a {@link TransactionTemplate} to mirror production OSIV semantics:
 * the filter must be enabled on the same Hibernate session that executes the query.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=order-tenant-isolation-test",
                "spring.kafka.producer.acks=all"
        })
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.authorized", "payment.failed", "inventory.insufficient", "order-topic"},
        brokerProperties = {"auto.create.topics.enable=true"})
@ActiveProfiles("test")
@DisplayName("Tenant isolation — order-service (integration, Testcontainers PostgreSQL)")
class OrderTenantIsolationIntegrationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    /** Same customer id in BOTH tenants — proves findMyOrders scopes by tenant, not just customer. */
    private static final String SHARED_CUSTOMER_ID = "42";

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_tenant_isolation_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired PlatformTransactionManager transactionManager;

    // External HTTP client, not under test — same recipe as OrderSagaIntegrationTest.
    @MockBean CustomerClient customerClient;

    private TransactionTemplate tx;

    private Order orderA1;
    private Order orderA2;
    private Order orderB1;

    @BeforeEach
    void seedTwoTenants() {
        tx = new TransactionTemplate(transactionManager);
        orderRepository.deleteAll();

        orderA1 = orderRepository.save(order(TENANT_A, "corr-tiso-a1", "ORD-TISO-A1"));
        orderA2 = orderRepository.save(order(TENANT_A, "corr-tiso-a2", "ORD-TISO-A2"));
        orderB1 = orderRepository.save(order(TENANT_B, "corr-tiso-b1", "ORD-TISO-B1"));
    }

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        orderRepository.deleteAll();
    }

    private static Order order(String tenantId, String correlationId, String reference) {
        return Order.builder()
                .tenantId(tenantId)
                .correlationId(correlationId)
                .reference(reference)
                .totalAmount(BigDecimal.valueOf(99.90))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId(SHARED_CUSTOMER_ID)
                .build();
    }

    private static void authenticateAs(String tenantId, String role) {
        SecurityPrincipal principal =
                new SecurityPrincipal("isolation@test.local", Long.valueOf(SHARED_CUSTOMER_ID), tenantId, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read paths that activate the tenant filter — expected GREEN.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Filtered list reads — findAllOrders / findMyOrders")
    class FilteredListReads {

        @Test
        @DisplayName("findAllOrders under tenant A returns only tenant A orders")
        void findAllOrdersIsTenantScoped() {
            TenantContext.setCurrentTenantId(TENANT_A);

            List<OrderResponse> orders = tx.execute(status -> orderService.findAllOrders());

            assertThat(orders)
                    .as("tenant A must see exactly its own orders, zero rows of tenant B")
                    .extracting(OrderResponse::reference)
                    .containsExactlyInAnyOrder("ORD-TISO-A1", "ORD-TISO-A2")
                    .doesNotContain("ORD-TISO-B1");
        }

        @Test
        @DisplayName("findAllOrders under tenant B returns only tenant B orders")
        void findAllOrdersIsTenantScopedSymmetric() {
            TenantContext.setCurrentTenantId(TENANT_B);

            List<OrderResponse> orders = tx.execute(status -> orderService.findAllOrders());

            assertThat(orders)
                    .as("tenant B must see exactly its own orders, zero rows of tenant A")
                    .extracting(OrderResponse::reference)
                    .containsExactly("ORD-TISO-B1");
        }

        @Test
        @DisplayName("findMyOrders scopes by tenant even when the same customerId exists in both tenants")
        void findMyOrdersIsTenantScopedForSharedCustomerId() {
            TenantContext.setCurrentTenantId(TENANT_A);
            authenticateAs(TENANT_A, "USER");

            List<OrderResponse> orders = tx.execute(status -> orderService.findMyOrders());

            assertThat(orders)
                    .as("customer 42 of tenant A must not see customer 42's orders in tenant B")
                    .extracting(OrderResponse::reference)
                    .containsExactlyInAnyOrder("ORD-TISO-A1", "ORD-TISO-A2")
                    .doesNotContain("ORD-TISO-B1");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status polling — tenant-keyed query. Expected GREEN.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Status polling — getOrderStatus")
    class StatusPolling {

        @Test
        @DisplayName("polling another tenant's correlationId is a 404, not a leak")
        void statusAcrossTenantsIsNotFound() {
            TenantContext.setCurrentTenantId(TENANT_A);

            assertThatThrownBy(() -> orderService.getOrderStatus("corr-tiso-b1"))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("polling own correlationId still works (positive control)")
        void statusSameTenantStillWorks() {
            TenantContext.setCurrentTenantId(TENANT_A);

            OrderStatusResponse status = orderService.getOrderStatus("corr-tiso-a1");

            assertThat(status.reference()).isEqualTo("ORD-TISO-A1");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findById — REQUIRED behaviour. RED today = documented leak:
    // repository.findById (= em.find) bypasses Hibernate filters, and the
    // ownership guard checks the principal, never the tenant. Fase 1b fixes it.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Read by id — required tenant isolation (RED today = cross-tenant leak)")
    class ReadById {

        @Test
        @DisplayName("findById across tenants must behave as not-found")
        void findByIdMustNotCrossTenants() {
            TenantContext.setCurrentTenantId(TENANT_A);

            assertThatThrownBy(() -> tx.execute(status ->
                    orderService.findById(orderB1.getOrderId())))
                    .as("tenant A reading tenant B's order by id must be a 404, not a leak")
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("findById within the same tenant still works (positive control)")
        void findByIdSameTenantStillWorks() {
            TenantContext.setCurrentTenantId(TENANT_A);

            OrderResponse response = tx.execute(status ->
                    orderService.findById(orderA1.getOrderId()));

            assertThat(response.reference()).isEqualTo("ORD-TISO-A1");
        }
    }
}
