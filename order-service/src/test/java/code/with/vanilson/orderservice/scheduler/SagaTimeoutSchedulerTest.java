package code.with.vanilson.orderservice.scheduler;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaTimeoutSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @InjectMocks
    private SagaTimeoutScheduler sagaTimeoutScheduler;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sagaTimeoutScheduler, "timeoutMinutes", 15);
        lenient().when(meterRegistry.counter(any())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
    }

    @Test
    @DisplayName("Should mark stuck orders as TIMEOUT")
    void shouldMarkStuckOrdersAsTimeout() {
        // Arrange
        Order order1 = Order.builder().orderId(1).correlationId("corr-1").status(OrderStatus.REQUESTED).build();
        Order order2 = Order.builder().orderId(2).correlationId("corr-2").status(OrderStatus.REQUESTED).build();
        List<Order> stuckOrders = List.of(order1, order2);

        when(orderRepository.findByStatusAndCreatedDateBefore(eq(OrderStatus.REQUESTED), any(LocalDateTime.class)))
                .thenReturn(stuckOrders);

        // Act
        sagaTimeoutScheduler.cancelTimedOutOrders();

        // Assert
        verify(orderRepository, times(2)).save(orderCaptor.capture());
        List<Order> savedOrders = orderCaptor.getAllValues();
        assertEquals(2, savedOrders.size());
        assertEquals(OrderStatus.TIMEOUT, savedOrders.get(0).getStatus());
        assertEquals(OrderStatus.TIMEOUT, savedOrders.get(1).getStatus());
    }

    @Test
    @DisplayName("Should do nothing when no stuck orders found")
    void shouldDoNothingWhenNoStuckOrdersFound() {
        // Arrange
        when(orderRepository.findByStatusAndCreatedDateBefore(eq(OrderStatus.REQUESTED), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        sagaTimeoutScheduler.cancelTimedOutOrders();

        // Assert
        verify(orderRepository, never()).save(any(Order.class));
    }
}
