package code.with.vanilson.orderservice.bdd;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderMapper;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.OrderResponse;
import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerSnapshotRepository;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderIllegalStateTransitionException;
import code.with.vanilson.orderservice.exception.OrderValidationException;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.outbox.OutboxRepository;
import code.with.vanilson.orderservice.payment.PaymentMethod;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BDD step definitions for the Fase 5 order fulfillment status flow. POJO + Mockito glue
 * (no Spring context) — drives the real {@link OrderService#updateStatusManually} over mocked
 * collaborators, so the whitelist / authorisation / transition rules are exercised behaviourally.
 */
public class OrderFulfillmentStepDefinitions {

    private static final Integer ORDER_ID = 500;
    private static final String OWNER_SELLER = "7";

    private OrderRepository orderRepository;
    private OrderLineService orderLineService;
    private OrderService orderService;
    private Order order;

    private OrderResponse result;
    private RuntimeException caught;

    @After
    public void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Given("a confirmed order owned by seller {string}")
    public void aConfirmedOrderOwnedBySeller(String sellerId) {
        orderRepository = mock(OrderRepository.class);
        orderLineService = mock(OrderLineService.class);
        OrderMapper orderMapper = mock(OrderMapper.class);
        CustomerClient customerClient = mock(CustomerClient.class);
        CustomerSnapshotRepository snapshotRepository = mock(CustomerSnapshotRepository.class);
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        MessageSource messageSource = mock(MessageSource.class);
        TenantHibernateFilterActivator filterActivator = mock(TenantHibernateFilterActivator.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(snapshotRepository.findById(anyString())).thenReturn(Optional.empty());
        lenient().when(orderMapper.fromOrder(any(), any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return new OrderResponse(o.getOrderId(), o.getReference(), o.getTotalAmount(),
                    "CREDIT_CARD", o.getCustomerId(), o.getStatus().name());
        });

        order = Order.builder()
                .orderId(ORDER_ID)
                .tenantId("default")
                .correlationId("corr-500")
                .reference("ORD-500")
                .totalAmount(BigDecimal.valueOf(120))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerId("42")
                .status(OrderStatus.CONFIRMED)
                .build();

        // updateStatusManually picks its repo call from the AMBIENT tenant:
        //   TenantContext.isPresent() ? findByOrderIdAndTenantId(id, tenant) : findById(id)
        // Cucumber instantiates every glue class in this package per scenario, so another
        // step-def class (OrderStepDefinitions) may already have bound a tenant on this thread.
        // Stub BOTH branches so the scenario is deterministic regardless of hook order.
        lenient().when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.findByOrderIdAndTenantId(eq(ORDER_ID), anyString()))
                .thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderLineService.sellerOwnsLineInOrder(eq(ORDER_ID), anyString()))
                .thenAnswer(inv -> sellerId.equals(inv.getArgument(1)));

        orderService = new OrderService(orderRepository, orderMapper, customerClient, snapshotRepository,
                orderLineService, outboxRepository, messageSource, filterActivator, meterRegistry,
                eventPublisher);

        result = null;
        caught = null;
    }

    @Given("the current actor is {string} with id {long}")
    public void theCurrentActorIs(String role, long id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("actor@test.com", id, "default", role), null));
    }

    @When("the actor sets the order status to {string}")
    public void theActorSetsTheOrderStatusTo(String status) {
        try {
            result = orderService.updateStatusManually(ORDER_ID, OrderStatus.valueOf(status));
        } catch (RuntimeException ex) {
            caught = ex;
        }
    }

    @Then("the order status becomes {string}")
    public void theOrderStatusBecomes(String status) {
        assertThat(caught).isNull();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(status);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.valueOf(status));
    }

    @Then("the shipped timestamp is recorded")
    public void theShippedTimestampIsRecorded() {
        assertThat(order.getShippedAt()).isNotNull();
    }

    @Then("the update is forbidden")
    public void theUpdateIsForbidden() {
        assertThat(caught).isInstanceOf(OrderForbiddenException.class);
    }

    @Then("the update is rejected as not allowed")
    public void theUpdateIsRejectedAsNotAllowed() {
        assertThat(caught).isInstanceOf(OrderValidationException.class);
    }

    @Then("the update is rejected as an illegal transition")
    public void theUpdateIsRejectedAsAnIllegalTransition() {
        assertThat(caught).isInstanceOf(OrderIllegalStateTransitionException.class);
    }
}
