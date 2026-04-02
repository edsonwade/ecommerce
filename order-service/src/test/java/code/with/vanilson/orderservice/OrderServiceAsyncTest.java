package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerInfo;
import code.with.vanilson.orderservice.exception.CustomerServiceUnavailableException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.outbox.OutboxEvent;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.orderservice.product.ProductPurchaseRequest;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceAsyncTest — Unit Tests (Phase 3)
 * <p>
 * Validates the async order creation flow:
 * - Returns correlationId (UUID string), not orderId
 * - Persists order in REQUESTED state
 * - Persists OutboxEvent with PENDING status targeting order.requested topic
 * - Never calls ProductClient or PaymentClient (moved to Kafka saga)
 * - Status polling endpoint works for all saga states
 * <p>
 * Framework: JUnit 5 + Mockito + AssertJ.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — Phase 3 Async Unit Tests")
class OrderServiceAsyncTest {

    @Mock private OrderRepository              orderRepository;
    @Mock private OrderMapper                  orderMapper;
    @Mock private CustomerClient               customerClient;
    @Mock private OrderLineService             orderLineService;
    @Mock private OutboxRepository             outboxRepository;
    @Mock private MessageSource                messageSource;
    @Mock private TenantHibernateFilterActivator filterActivator;

    @InjectMocks
    private OrderService orderService;

    private CustomerInfo  validCustomer;
    private OrderRequest  validRequest;
    private Order         savedOrder;

    @BeforeEach
    void setUp() {
        // Set up tenant context for tests
        TenantContext.setCurrentTenantId("test-tenant-123");

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        validCustomer = new CustomerInfo(
                "cust-001", "Ana", "Silva", "ana@example.com", null);

        validRequest = new OrderRequest(
                null,
                "REF-PHASE3-001",
                BigDecimal.valueOf(299.99),
                PaymentMethod.CREDIT_CARD,
                "cust-001",
                List.of(new ProductPurchaseRequest(1, 2.0))
        );

        savedOrder = Order.builder()
                .orderId(42)
                .correlationId("test-correlation-id")
                .reference("REF-PHASE3-001")
                .totalAmount(BigDecimal.valueOf(299.99))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId("cust-001")
                .status(OrderStatus.REQUESTED)
                .tenantId("test-tenant-123")
                .build();
    }

    @AfterEach
    void cleanup() {
        // Clear tenant context after each test
        TenantContext.clear();
    }

    // -------------------------------------------------------
    // createOrder — async (202 Accepted)
    // -------------------------------------------------------

    @Nested
    @DisplayName("createOrder — async (returns correlationId, 202 Accepted)")
    class CreateOrder {

        @Test
        @DisplayName("should return a UUID-format correlationId (not the DB orderId)")
        void shouldReturnCorrelationId() {
            when(customerClient.findCustomerById("cust-001")).thenReturn(Optional.of(validCustomer));
            when(orderRepository.existsByReference("REF-PHASE3-001")).thenReturn(false);
            when(orderMapper.toOrder(any())).thenReturn(savedOrder);
            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(outboxRepository.save(any())).thenReturn(new OutboxEvent());

            String correlationId = orderService.createOrder(validRequest);

            assertThat(correlationId)
                    .as("createOrder must return a non-null UUID-format correlationId")
                    .isNotNull()
                    .isNotBlank()
                    .matches("[0-9a-f\\-]{36}");
        }

        @Test
        @DisplayName("should save order in REQUESTED status")
        void shouldPersistOrderAsRequested() {
            when(customerClient.findCustomerById("cust-001")).thenReturn(Optional.of(validCustomer));
            when(orderRepository.existsByReference(anyString())).thenReturn(false);
            when(orderMapper.toOrder(any())).thenReturn(savedOrder);
            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(outboxRepository.save(any())).thenReturn(new OutboxEvent());

            orderService.createOrder(validRequest);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus())
                    .as("Order must be saved in REQUESTED state — saga processes it async")
                    .isEqualTo(OrderStatus.REQUESTED);
        }

        @Test
        @DisplayName("should persist OutboxEvent in the same call (atomic dual-write)")
        void shouldPersistOutboxEvent() {
            when(customerClient.findCustomerById("cust-001")).thenReturn(Optional.of(validCustomer));
            when(orderRepository.existsByReference(anyString())).thenReturn(false);
            when(orderMapper.toOrder(any())).thenReturn(savedOrder);
            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(outboxRepository.save(any())).thenReturn(new OutboxEvent());

            orderService.createOrder(validRequest);

            verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("OutboxEvent must target order.requested topic with PENDING status")
        void shouldPersistOutboxWithCorrectTopicAndStatus() {
            when(customerClient.findCustomerById("cust-001")).thenReturn(Optional.of(validCustomer));
            when(orderRepository.existsByReference(anyString())).thenReturn(false);
            when(orderMapper.toOrder(any())).thenReturn(savedOrder);
            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(outboxRepository.save(any())).thenReturn(new OutboxEvent());

            orderService.createOrder(validRequest);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());
            OutboxEvent outbox = captor.getValue();

            assertThat(outbox.getTopic())
                    .as("OutboxEvent must target order.requested topic")
                    .isEqualTo("order.requested");
            assertThat(outbox.getStatus())
                    .as("OutboxEvent must start in PENDING status")
                    .isEqualTo(OutboxEvent.OutboxStatus.PENDING);
            assertThat(outbox.getPayload())
                    .as("OutboxEvent payload must be non-blank JSON")
                    .isNotBlank();
            assertThat(outbox.getRetryCount())
                    .as("Retry count must start at 0")
                    .isZero();
        }

        @Test
        @DisplayName("correlationId on saved order must match the returned correlationId")
        void savedOrderCorrelationIdMatchesReturned() {
            when(customerClient.findCustomerById("cust-001")).thenReturn(Optional.of(validCustomer));
            when(orderRepository.existsByReference(anyString())).thenReturn(false);
            when(orderMapper.toOrder(any())).thenReturn(savedOrder);
            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(outboxRepository.save(any())).thenReturn(new OutboxEvent());

            String returnedId = orderService.createOrder(validRequest);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getCorrelationId())
                    .as("correlationId on saved Order must equal the returned correlationId")
                    .isEqualTo(returnedId);
        }

        @Test
        @DisplayName("should throw CustomerServiceUnavailableException when customer not found — no DB writes")
        void shouldThrowAndNotWriteWhenCustomerMissing() {
            when(customerClient.findCustomerById("cust-001")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(validRequest))
                    .isInstanceOf(CustomerServiceUnavailableException.class)
                    .hasMessageContaining("order.customer.not.found");

            verify(orderRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------
    // getOrderStatus — saga polling
    // -------------------------------------------------------

    @Nested
    @DisplayName("getOrderStatus — saga state polling")
    class GetOrderStatus {

        @Test
        @DisplayName("should return REQUESTED immediately after order creation")
        void shouldReturnRequested() {
            when(orderRepository.findByCorrelationId("test-correlation-id"))
                    .thenReturn(Optional.of(savedOrder));

            OrderStatusResponse status = orderService.getOrderStatus("test-correlation-id");

            assertThat(status).isNotNull();
            assertThat(status.status()).isEqualTo("REQUESTED");
            assertThat(status.correlationId()).isEqualTo("test-correlation-id");
        }

        @Test
        @DisplayName("should return CONFIRMED after saga completes")
        void shouldReturnConfirmedAfterSaga() {
            savedOrder.setStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findByCorrelationId("test-correlation-id"))
                    .thenReturn(Optional.of(savedOrder));

            OrderStatusResponse status = orderService.getOrderStatus("test-correlation-id");

            assertThat(status.status()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("should return CANCELLED when saga compensation triggered")
        void shouldReturnCancelledAfterCompensation() {
            savedOrder.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findByCorrelationId("test-correlation-id"))
                    .thenReturn(Optional.of(savedOrder));

            OrderStatusResponse status = orderService.getOrderStatus("test-correlation-id");

            assertThat(status.status()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("should throw OrderNotFoundException for unknown correlationId")
        void shouldThrowForUnknownCorrelationId() {
            when(orderRepository.findByCorrelationId("unknown-id"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderStatus("unknown-id"))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("order.not.found");
        }
    }

    // -------------------------------------------------------
    // updateStatus — called by OrderSagaConsumer
    // -------------------------------------------------------

    @Nested
    @DisplayName("updateStatus — invoked by OrderSagaConsumer")
    class UpdateStatus {

        @Test
        @DisplayName("should update order to CONFIRMED when payment.authorized consumed")
        void shouldUpdateToConfirmed() {
            when(orderRepository.findByCorrelationId("test-correlation-id"))
                    .thenReturn(Optional.of(savedOrder));

            orderService.updateStatus("test-correlation-id", OrderStatus.CONFIRMED);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("should update order to CANCELLED when payment.failed consumed")
        void shouldUpdateToCancelled() {
            when(orderRepository.findByCorrelationId("test-correlation-id"))
                    .thenReturn(Optional.of(savedOrder));

            orderService.updateStatus("test-correlation-id", OrderStatus.CANCELLED);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("should do nothing (idempotent) when correlationId not found")
        void shouldBeIdempotentWhenNotFound() {
            when(orderRepository.findByCorrelationId("ghost-id"))
                    .thenReturn(Optional.empty());

            orderService.updateStatus("ghost-id", OrderStatus.CONFIRMED);

            verify(orderRepository, never()).save(any());
        }
    }
}
