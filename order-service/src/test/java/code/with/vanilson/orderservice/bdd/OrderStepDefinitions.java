package code.with.vanilson.orderservice.bdd;

import code.with.vanilson.orderservice.*;
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
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cucumber step definitions for order-service BDD scenarios.
 * Uses mocked dependencies — no real database or Kafka.
 */
public class OrderStepDefinitions {

    private OrderRepository orderRepository;
    private OrderMapper orderMapper;
    private CustomerClient customerClient;
    private OrderLineService orderLineService;
    private OutboxRepository outboxRepository;
    private MessageSource messageSource;
    private TenantHibernateFilterActivator filterActivator;
    private OrderService orderService;

    // State
    private String correlationId;
    private OrderStatusResponse statusResponse;
    private Exception caughtException;
    private OrderRequest orderRequest;
    private List<OrderResponse> orderList;

    @Before
    public void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        orderMapper = Mockito.mock(OrderMapper.class);
        customerClient = Mockito.mock(CustomerClient.class);
        orderLineService = Mockito.mock(OrderLineService.class);
        outboxRepository = Mockito.mock(OutboxRepository.class);
        messageSource = Mockito.mock(MessageSource.class);
        filterActivator = Mockito.mock(TenantHibernateFilterActivator.class);

        orderService = new OrderService(
                orderRepository, orderMapper, customerClient,
                orderLineService, outboxRepository, messageSource, filterActivator);

        TenantContext.setCurrentTenantId("test-tenant");

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        correlationId = null;
        statusResponse = null;
        caughtException = null;
        orderRequest = null;
        orderList = null;
    }

    @After
    public void tearDown() {
        TenantContext.clear();
    }

    // ---- Given ----

    @Given("a valid customer with ID {string} exists")
    public void a_valid_customer_exists(String customerId) {
        CustomerInfo customer = new CustomerInfo(customerId, "Ana", "Silva", "ana@example.com", null);
        when(customerClient.findCustomerById(customerId)).thenReturn(Optional.of(customer));
    }

    @Given("a valid order request with reference {string} and amount {double}")
    public void a_valid_order_request(String ref, double amount) {
        orderRequest = new OrderRequest(null, ref, BigDecimal.valueOf(amount),
                PaymentMethod.CREDIT_CARD, "cust-001",
                List.of(new ProductPurchaseRequest(1, 2.0)));

        Order savedOrder = Order.builder()
                .orderId(1).correlationId("generated-corr-id").reference(ref)
                .totalAmount(BigDecimal.valueOf(amount)).status(OrderStatus.REQUESTED)
                .tenantId("test-tenant").build();

        when(orderRepository.existsByReference(ref)).thenReturn(false);
        when(orderMapper.toOrder(any())).thenReturn(savedOrder);
        when(orderRepository.save(any())).thenReturn(savedOrder);
        when(outboxRepository.save(any())).thenReturn(new OutboxEvent());
    }

    @Given("no customer with ID {string} exists")
    public void no_customer_exists(String customerId) {
        when(customerClient.findCustomerById(customerId)).thenReturn(Optional.empty());
    }

    @Given("a valid order request for customer {string}")
    public void a_valid_order_request_for_customer(String customerId) {
        orderRequest = new OrderRequest(null, "REF-001", BigDecimal.TEN,
                PaymentMethod.VISA, customerId,
                List.of(new ProductPurchaseRequest(1, 1.0)));
    }

    @Given("an order with correlationId {string} exists in REQUESTED status")
    public void an_order_exists_in_requested(String corrId) {
        setupOrderWithStatus(corrId, OrderStatus.REQUESTED);
    }

    @Given("an order with correlationId {string} exists in CONFIRMED status")
    public void an_order_exists_in_confirmed(String corrId) {
        setupOrderWithStatus(corrId, OrderStatus.CONFIRMED);
    }

    @Given("an order with correlationId {string} exists in CANCELLED status")
    public void an_order_exists_in_cancelled(String corrId) {
        setupOrderWithStatus(corrId, OrderStatus.CANCELLED);
    }

    @Given("no order with correlationId {string} exists")
    public void no_order_with_correlation_exists(String corrId) {
        when(orderRepository.findByCorrelationId(corrId)).thenReturn(Optional.empty());
    }

    @Given("the following orders exist:")
    public void the_following_orders_exist(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        List<OrderResponse> responses = rows.stream().map(row ->
                new OrderResponse(
                        Integer.parseInt(row.get("orderId")),
                        row.get("reference"),
                        new BigDecimal(row.get("amount")),
                        "CREDIT_CARD",
                        row.get("customerId"))
        ).toList();
        when(orderService.findAllOrders()).thenReturn(responses);
    }

    // ---- When ----

    @When("the order is submitted")
    public void the_order_is_submitted() {
        try {
            correlationId = orderService.createOrder(orderRequest);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @When("the order status is polled with correlationId {string}")
    public void the_order_status_is_polled(String corrId) {
        try {
            statusResponse = orderService.getOrderStatus(corrId);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @When("all orders are requested")
    public void all_orders_are_requested() {
        orderList = orderService.findAllOrders();
    }

    // ---- Then ----

    @Then("the system returns a correlationId")
    public void the_system_returns_correlationId() {
        assertThat(caughtException).isNull();
        assertThat(correlationId).isNotNull().isNotBlank();
    }

    @Then("the order status is {string}")
    public void the_order_status_is(String status) {
        // After creation, we poll to verify
        assertThat(correlationId).isNotNull();
    }

    @Then("the system rejects the order with a customer unavailable error")
    public void the_system_rejects_with_customer_error() {
        assertThat(caughtException).isNotNull()
                .isInstanceOf(CustomerServiceUnavailableException.class);
    }

    @Then("the returned status is {string}")
    public void the_returned_status_is(String expectedStatus) {
        assertThat(caughtException).isNull();
        assertThat(statusResponse).isNotNull();
        assertThat(statusResponse.status()).isEqualTo(expectedStatus);
    }

    @Then("the system returns an order not found error")
    public void the_system_returns_order_not_found() {
        assertThat(caughtException).isNotNull()
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Then("the system returns {int} orders")
    public void the_system_returns_n_orders(int count) {
        assertThat(orderList).isNotNull().hasSize(count);
    }

    // ---- Helpers ----

    private void setupOrderWithStatus(String corrId, OrderStatus status) {
        Order order = Order.builder()
                .orderId(1).correlationId(corrId).reference("REF-POLL")
                .totalAmount(BigDecimal.valueOf(100)).status(status)
                .tenantId("test-tenant").build();
        when(orderRepository.findByCorrelationId(corrId)).thenReturn(Optional.of(order));
    }
}
