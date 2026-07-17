package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerSnapshotRepository;
import code.with.vanilson.orderservice.event.OrderStatusChangedEvent;
import code.with.vanilson.orderservice.exception.OrderIllegalStateTransitionException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.outbox.OutboxEvent;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

/**
 * OrderServiceApplyRefundTest — Unit tests for {@link OrderService#applyRefund} (Fase 6).
 * <p>
 * Covers the transition + outbox write, idempotent no-op on a redelivered event, and the
 * illegal-transition failure path (no save, no outbox, no event — the caller does not
 * acknowledge, so Kafka retries/DLQs per the documented Fase 6 failure semantics).
 */
@DisplayName("OrderService.applyRefund — Fase 6 (unit)")
class OrderServiceApplyRefundTest {

    private static final Integer ORDER_ID = 100;

    private OrderRepository orderRepository;
    private OutboxRepository outboxRepository;
    private ApplicationEventPublisher eventPublisher;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        CustomerClient customerClient = mock(CustomerClient.class);
        CustomerSnapshotRepository snapshotRepository = mock(CustomerSnapshotRepository.class);
        OrderLineService orderLineService = mock(OrderLineService.class);
        outboxRepository = mock(OutboxRepository.class);
        MessageSource messageSource = mock(MessageSource.class);
        TenantHibernateFilterActivator filterActivator = mock(TenantHibernateFilterActivator.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        orderService = new OrderService(orderRepository, orderMapper, customerClient, snapshotRepository,
                orderLineService, outboxRepository, messageSource, filterActivator, meterRegistry,
                eventPublisher);
    }

    private static Order orderWithStatus(OrderStatus status) {
        return Order.builder()
                .orderId(ORDER_ID)
                .tenantId("tenant-refund")
                .correlationId("corr-refund-1")
                .reference("ORD-REFUND-1")
                .totalAmount(BigDecimal.valueOf(150.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId("42")
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("Successful refund")
    class SuccessfulRefund {

        @Test
        @DisplayName("CONFIRMED → REFUNDED: saves, writes an order.refunded outbox row, publishes SSE event")
        void refundsConfirmedOrder() {
            Order order = orderWithStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.applyRefund(ORDER_ID, "evt-refund-1", Instant.now());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            verify(orderRepository).save(order);

            var captor = forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());
            OutboxEvent outbox = captor.getValue();
            assertThat(outbox.getEventId()).isEqualTo("evt-refund-1");
            assertThat(outbox.getCorrelationId()).isEqualTo("corr-refund-1");
            assertThat(outbox.getTenantId()).isEqualTo("tenant-refund");
            assertThat(outbox.getTopic()).isEqualTo("order.refunded");
            assertThat(outbox.getPartitionKey()).isEqualTo("corr-refund-1");
            assertThat(outbox.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);

            verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
        }

        @Test
        @DisplayName("SHIPPED → REFUNDED is also a legal transition")
        void refundsShippedOrder() {
            Order order = orderWithStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.applyRefund(ORDER_ID, "evt-refund-2", Instant.now());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        }
    }

    @Nested
    @DisplayName("Idempotency + failure semantics")
    class IdempotencyAndFailures {

        @Test
        @DisplayName("redelivered event on an already-REFUNDED order is a no-op")
        void alreadyRefundedIsNoOp() {
            Order order = orderWithStatus(OrderStatus.REFUNDED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.applyRefund(ORDER_ID, "evt-refund-3", Instant.now());

            verify(orderRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("order not found throws OrderNotFoundException")
        void orderNotFoundThrows() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.applyRefund(ORDER_ID, "evt-refund-4", Instant.now()))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("illegal transition (REQUESTED → REFUNDED) throws, no save, no outbox, no event")
        void illegalTransitionThrows() {
            Order order = orderWithStatus(OrderStatus.REQUESTED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.applyRefund(ORDER_ID, "evt-refund-5", Instant.now()))
                    .isInstanceOf(OrderIllegalStateTransitionException.class);

            verify(orderRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
