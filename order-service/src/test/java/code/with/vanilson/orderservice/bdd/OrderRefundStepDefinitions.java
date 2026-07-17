package code.with.vanilson.orderservice.bdd;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderMapper;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerSnapshotRepository;
import code.with.vanilson.orderservice.exception.OrderIllegalStateTransitionException;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.outbox.OutboxEvent;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BDD step definitions for the Fase 6 order-refund flow. POJO + Mockito glue (no Spring
 * context) — drives the real {@link OrderService#applyRefund} over mocked collaborators.
 */
public class OrderRefundStepDefinitions {

    private static final Integer ORDER_ID = 700;

    private OrderRepository orderRepository;
    private OutboxRepository outboxRepository;
    private OrderService orderService;
    private Order order;

    private RuntimeException caught;

    @After
    public void cleanup() {
        TenantContext.clear();
    }

    private void bootstrap(OrderStatus initialStatus) {
        orderRepository = mock(OrderRepository.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        CustomerClient customerClient = mock(CustomerClient.class);
        CustomerSnapshotRepository snapshotRepository = mock(CustomerSnapshotRepository.class);
        OrderLineService orderLineService = mock(OrderLineService.class);
        outboxRepository = mock(OutboxRepository.class);
        MessageSource messageSource = mock(MessageSource.class);
        TenantHibernateFilterActivator filterActivator = mock(TenantHibernateFilterActivator.class);
        io.micrometer.core.instrument.MeterRegistry meterRegistry =
                mock(io.micrometer.core.instrument.MeterRegistry.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        order = Order.builder()
                .orderId(ORDER_ID)
                .tenantId("default")
                .correlationId("corr-refund-bdd")
                .reference("ORD-REFUND-BDD")
                .totalAmount(BigDecimal.valueOf(150.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId("42")
                .status(initialStatus)
                .build();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService = new OrderService(orderRepository, orderMapper, customerClient, snapshotRepository,
                orderLineService, outboxRepository, messageSource, filterActivator, meterRegistry,
                eventPublisher);

        caught = null;
    }

    @Given("a confirmed order exists for the refund")
    public void a_confirmed_order_exists() {
        bootstrap(OrderStatus.CONFIRMED);
    }

    @Given("an already-refunded order exists for the refund")
    public void an_already_refunded_order_exists() {
        bootstrap(OrderStatus.REFUNDED);
    }

    @Given("a requested order exists for the refund")
    public void a_requested_order_exists() {
        bootstrap(OrderStatus.REQUESTED);
    }

    @When("a payment.refunded event arrives for that order")
    public void a_payment_refunded_event_arrives() {
        try {
            orderService.applyRefund(ORDER_ID, "evt-refund-bdd", Instant.now());
        } catch (RuntimeException e) {
            caught = e;
        }
    }

    @Then("the refunded order's status becomes {string}")
    public void the_refunded_order_status_becomes(String status) {
        assertThat(caught).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.valueOf(status));
    }

    @Then("an order.refunded outbox event is written")
    public void an_order_refunded_outbox_event_is_written() {
        verify(outboxRepository).save(org.mockito.ArgumentMatchers.argThat(
                (OutboxEvent o) -> o.getTopic().equals("order.refunded")));
    }

    @Then("no new outbox event is written")
    public void no_new_outbox_event_is_written() {
        assertThat(caught).isNull();
        verify(outboxRepository, org.mockito.Mockito.never()).save(any());
    }

    @Then("the refund is rejected as an illegal transition")
    public void the_refund_is_rejected_as_an_illegal_transition() {
        assertThat(caught).isInstanceOf(OrderIllegalStateTransitionException.class);
        verify(outboxRepository, org.mockito.Mockito.never()).save(any());
    }
}
