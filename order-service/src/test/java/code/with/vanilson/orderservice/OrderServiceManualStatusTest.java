package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerSnapshotRepository;
import code.with.vanilson.orderservice.event.OrderStatusChangedEvent;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderIllegalStateTransitionException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.exception.OrderValidationException;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceManualStatusTest — Unit tests for the Fase 5 manual fulfillment update
 * ({@link OrderService#updateStatusManually}).
 * <p>
 * Covers the whitelist (only SHIPPED/DELIVERED), authorisation (ADMIN any / SELLER must own a
 * line / SELLER without a line → 403), the transition guard (illegal transition → 409),
 * timestamp stamping, event publication for SSE, and idempotent no-op on a repeated status.
 * No tenant is bound, so the plain {@code findById} lookup path is exercised.
 *
 * @author vamuhong
 */
@DisplayName("OrderService.updateStatusManually — fulfillment (unit)")
class OrderServiceManualStatusTest {

    private static final Integer ORDER_ID = 1;

    private OrderRepository orderRepository;
    private OrderMapper orderMapper;
    private OrderLineService orderLineService;
    private CustomerSnapshotRepository snapshotRepository;
    private ApplicationEventPublisher eventPublisher;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // updateStatusManually reads the AMBIENT tenant to choose its repo call
        // (findByOrderIdAndTenantId vs findById). TenantContext is a ThreadLocal, so a
        // previous test on this thread could leak one in. Clear it so these tests
        // deterministically exercise the plain findById branch they stub.
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        orderRepository = mock(OrderRepository.class);
        orderMapper = mock(OrderMapper.class);
        CustomerClient customerClient = mock(CustomerClient.class);
        snapshotRepository = mock(CustomerSnapshotRepository.class);
        orderLineService = mock(OrderLineService.class);
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        MessageSource messageSource = mock(MessageSource.class);
        TenantHibernateFilterActivator filterActivator = mock(TenantHibernateFilterActivator.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(snapshotRepository.findById(anyString())).thenReturn(Optional.empty());
        lenient().when(orderMapper.fromOrder(any(), any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return new OrderResponse(o.getOrderId(), o.getReference(), o.getTotalAmount(),
                    "CREDIT_CARD", o.getCustomerId(), o.getStatus().name());
        });

        orderService = new OrderService(orderRepository, orderMapper, customerClient, snapshotRepository,
                orderLineService, outboxRepository, messageSource, filterActivator, meterRegistry,
                eventPublisher);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static Order orderWithStatus(OrderStatus status) {
        return Order.builder()
                .orderId(ORDER_ID)
                .tenantId("default")
                .correlationId("corr-1")
                .reference("ORD-1")
                .totalAmount(BigDecimal.valueOf(99.90))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId("42")
                .status(status)
                .build();
    }

    private void authenticateAs(long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user@test.com", userId, "default", role), null));
    }

    @Nested
    @DisplayName("Whitelist")
    class Whitelist {

        @Test
        @DisplayName("rejects a non-fulfillment status (CONFIRMED) with 400 before any lookup")
        void rejectsNonFulfillmentStatus() {
            authenticateAs(1L, "ADMIN");

            assertThatThrownBy(() -> orderService.updateStatusManually(ORDER_ID, OrderStatus.CONFIRMED))
                    .isInstanceOf(OrderValidationException.class)
                    .hasMessageContaining("order.status.update.not.allowed");

            verify(orderRepository, never()).findById(anyInt());
        }

        @Test
        @DisplayName("rejects REFUNDED via this endpoint (400)")
        void rejectsRefunded() {
            authenticateAs(1L, "ADMIN");

            assertThatThrownBy(() -> orderService.updateStatusManually(ORDER_ID, OrderStatus.REFUNDED))
                    .isInstanceOf(OrderValidationException.class);
        }
    }

    @Nested
    @DisplayName("Authorisation")
    class Authorisation {

        @Test
        @DisplayName("ADMIN advances any confirmed order to SHIPPED — stamps shippedAt + publishes event")
        void adminShipsConfirmedOrder() {
            authenticateAs(1L, "ADMIN");
            Order order = orderWithStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.updateStatusManually(ORDER_ID, OrderStatus.SHIPPED);

            assertThat(response.status()).isEqualTo("SHIPPED");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(order.getShippedAt()).isNotNull();
            verify(orderRepository).save(order);
            verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
        }

        @Test
        @DisplayName("SELLER owning a line in the order may advance it")
        void sellerOwningLineShipsOrder() {
            authenticateAs(7L, "SELLER");
            Order order = orderWithStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderLineService.sellerOwnsLineInOrder(ORDER_ID, "7")).thenReturn(true);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.updateStatusManually(ORDER_ID, OrderStatus.SHIPPED);

            assertThat(response.status()).isEqualTo("SHIPPED");
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("SELLER without a line in the order is forbidden (403)")
        void sellerWithoutLineForbidden() {
            authenticateAs(8L, "SELLER");
            Order order = orderWithStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderLineService.sellerOwnsLineInOrder(ORDER_ID, "8")).thenReturn(false);

            assertThatThrownBy(() -> orderService.updateStatusManually(ORDER_ID, OrderStatus.SHIPPED))
                    .isInstanceOf(OrderForbiddenException.class)
                    .hasMessageContaining("order.status.update.forbidden");

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("Transition guard + lifecycle")
    class TransitionGuard {

        @Test
        @DisplayName("throws 404 when the order does not exist")
        void notFound() {
            authenticateAs(1L, "ADMIN");
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateStatusManually(ORDER_ID, OrderStatus.SHIPPED))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("rejects an illegal transition (REQUESTED → SHIPPED) with 409")
        void illegalTransition() {
            authenticateAs(1L, "ADMIN");
            Order order = orderWithStatus(OrderStatus.REQUESTED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateStatusManually(ORDER_ID, OrderStatus.SHIPPED))
                    .isInstanceOf(OrderIllegalStateTransitionException.class)
                    .hasMessageContaining("order.status.transition.invalid");

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("SHIPPED → DELIVERED stamps deliveredAt")
        void deliveredStampsTimestamp() {
            authenticateAs(1L, "ADMIN");
            Order order = orderWithStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.updateStatusManually(ORDER_ID, OrderStatus.DELIVERED);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(order.getDeliveredAt()).isNotNull();
        }

        @Test
        @DisplayName("re-sending the current status is an idempotent no-op (no save, no event)")
        void idempotentNoOp() {
            authenticateAs(1L, "ADMIN");
            Order order = orderWithStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.updateStatusManually(ORDER_ID, OrderStatus.SHIPPED);

            assertThat(response.status()).isEqualTo("SHIPPED");
            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
