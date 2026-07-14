package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerSnapshotRepository;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceFindByIdTest — Unit tests for tenant-scoped by-id reads (B3 Fase 1b).
 * <p>
 * {@code findById} used {@code repository.findById} (= {@code em.find}), which the Hibernate
 * tenant filter does not apply to, and its ownership guard checks the principal, not the tenant
 * — so a caller of tenant A could read tenant B's order by guessing its id. The fix routes the
 * read through {@link OrderRepository#findByOrderIdAndTenantId} whenever a tenant is bound. These
 * tests pin that branch: tenant-scoped query when a tenant is present, a plain lookup otherwise,
 * and a not-found (never a leak) across tenants.
 *
 * @author vamuhong
 */
@DisplayName("OrderService.findById — tenant isolation (unit)")
class OrderServiceFindByIdTest {

    private OrderRepository orderRepository;
    private OrderMapper orderMapper;
    private CustomerSnapshotRepository snapshotRepository;
    private OrderService orderService;

    private static final Integer ORDER_ID = 1;
    private static final String TENANT_A = "tenant-a";

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderMapper = mock(OrderMapper.class);
        CustomerClient customerClient = mock(CustomerClient.class);
        snapshotRepository = mock(CustomerSnapshotRepository.class);
        OrderLineService orderLineService = mock(OrderLineService.class);
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        org.springframework.context.MessageSource messageSource =
                mock(org.springframework.context.MessageSource.class);
        TenantHibernateFilterActivator filterActivator = mock(TenantHibernateFilterActivator.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // No customer snapshot — findById maps with a null snapshot, which is a valid path.
        lenient().when(snapshotRepository.findById(anyString())).thenReturn(Optional.empty());

        orderService = new OrderService(orderRepository, orderMapper, customerClient, snapshotRepository,
                orderLineService, outboxRepository, messageSource, filterActivator, meterRegistry,
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static Order order(String tenantId) {
        return Order.builder()
                .orderId(ORDER_ID)
                .tenantId(tenantId)
                .correlationId("corr-1")
                .reference("ORD-1")
                .totalAmount(BigDecimal.valueOf(99.90))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId("42")
                .status(OrderStatus.REQUESTED)
                .build();
    }

    @Test
    @DisplayName("queries by tenant when a tenant is bound (no em.find leak)")
    void usesTenantScopedQueryWhenTenantBound() {
        TenantContext.setCurrentTenantId(TENANT_A);
        Order order = order(TENANT_A);
        when(orderRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_A)).thenReturn(Optional.of(order));
        when(orderMapper.fromOrder(eq(order), any())).thenReturn(new OrderResponse(
                ORDER_ID, "ORD-1", BigDecimal.valueOf(99.90), "CREDIT_CARD", "42", "REQUESTED"));

        OrderResponse response = orderService.findById(ORDER_ID);

        assertThat(response.reference()).isEqualTo("ORD-1");
        verify(orderRepository).findByOrderIdAndTenantId(ORDER_ID, TENANT_A);
        verify(orderRepository, never()).findById(anyInt());
    }

    @Test
    @DisplayName("across tenants is a 404, not a cross-tenant leak")
    void acrossTenantsIsNotFound() {
        TenantContext.setCurrentTenantId(TENANT_A);
        when(orderRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("order.not.found");
        verify(orderRepository, never()).findById(anyInt());
    }

    @Test
    @DisplayName("falls back to a plain lookup when no tenant is bound (internal/single-tenant path)")
    void fallsBackToPlainLookupWithoutTenant() {
        // TenantContext deliberately not set.
        Order order = order("default");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderMapper.fromOrder(eq(order), any())).thenReturn(new OrderResponse(
                ORDER_ID, "ORD-1", BigDecimal.valueOf(99.90), "CREDIT_CARD", "42", "REQUESTED"));

        OrderResponse response = orderService.findById(ORDER_ID);

        assertThat(response.reference()).isEqualTo("ORD-1");
        verify(orderRepository).findById(ORDER_ID);
        verify(orderRepository, never()).findByOrderIdAndTenantId(anyInt(), anyString());
    }
}
