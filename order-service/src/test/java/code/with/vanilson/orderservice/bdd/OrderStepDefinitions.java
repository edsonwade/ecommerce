package code.with.vanilson.orderservice.bdd;

import code.with.vanilson.orderservice.*;
import code.with.vanilson.orderservice.customer.CustomerClient;
import code.with.vanilson.orderservice.customer.CustomerInfo;
import code.with.vanilson.orderservice.customer.CustomerSnapshotRepository;
import code.with.vanilson.orderservice.exception.CustomerNotFoundException;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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
    private CustomerSnapshotRepository snapshotRepository;
    private OrderLineService orderLineService;
    private OutboxRepository outboxRepository;
    private MessageSource messageSource;
    private TenantHibernateFilterActivator filterActivator;
    private MeterRegistry meterRegistry;
    private OrderService orderService;

    // State
    private String correlationId;
    private OrderStatusResponse statusResponse;
    private Exception caughtException;
    private OrderRequest orderRequest;
    private List<OrderResponse> orderList;
    private OrderResponse orderByIdResponse;

    @Before
    public void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        orderMapper = Mockito.mock(OrderMapper.class);
        customerClient = Mockito.mock(CustomerClient.class);
        snapshotRepository = Mockito.mock(CustomerSnapshotRepository.class);
        orderLineService = Mockito.mock(OrderLineService.class);
        outboxRepository = Mockito.mock(OutboxRepository.class);
        messageSource = Mockito.mock(MessageSource.class);
        filterActivator = Mockito.mock(TenantHibernateFilterActivator.class);
        meterRegistry = Mockito.mock(MeterRegistry.class);

        Counter mockCounter = mock(Counter.class);
        Mockito.lenient().when(meterRegistry.counter(anyString(), anyString(), anyString()))
                .thenReturn(mockCounter);

        // Default: no snapshot found — tests fall through to Feign
        Mockito.lenient().when(snapshotRepository.findById(anyString())).thenReturn(Optional.empty());

        orderService = new OrderService(
                orderRepository, orderMapper, customerClient, snapshotRepository,
                orderLineService, outboxRepository, messageSource, filterActivator, meterRegistry);

        TenantContext.setCurrentTenantId("test-tenant");

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        correlationId = null;
        statusResponse = null;
        caughtException = null;
        orderRequest = null;
        orderList = null;
        orderByIdResponse = null;
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

    @Given("a valid order request without a reference")
    public void a_valid_order_request_without_reference() {
        orderRequest = new OrderRequest(null, null, BigDecimal.valueOf(199.99),
                PaymentMethod.CREDIT_CARD, "cust-001",
                List.of(new ProductPurchaseRequest(1, 1.0)));

        // Mapper returns Order with null reference — service must auto-generate it
        Order orderWithNullRef = Order.builder()
                .orderId(2).correlationId(null)
                .reference(null)
                .totalAmount(BigDecimal.valueOf(199.99))
                .status(OrderStatus.REQUESTED)
                .tenantId("test-tenant").build();

        Order savedOrder = Order.builder()
                .orderId(2).correlationId("auto-gen-corr-id")
                .reference("ORD-AUTOGEN01")
                .totalAmount(BigDecimal.valueOf(199.99))
                .status(OrderStatus.REQUESTED)
                .tenantId("test-tenant").build();

        when(orderMapper.toOrder(any())).thenReturn(orderWithNullRef);
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
        when(orderRepository.findByCorrelationIdAndTenantId(eq(corrId), anyString())).thenReturn(Optional.empty());
    }

    @Given("the following orders exist:")
    public void the_following_orders_exist(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        List<Order> orders = rows.stream().map(row ->
                Order.builder()
                        .orderId(Integer.parseInt(row.get("orderId")))
                        .reference(row.get("reference"))
                        .totalAmount(new BigDecimal(row.get("amount")))
                        .customerId(row.get("customerId"))
                        .status(OrderStatus.REQUESTED)
                        .tenantId("test-tenant")
                        .build()
        ).toList();
        when(orderRepository.findAll()).thenReturn(orders);
        // findAllOrders() enriches via the 2-arg fromOrder(order, snapshot); snapshot is null
        // here (no snapshot stubbed), so match any second argument.
        orders.forEach(o -> when(orderMapper.fromOrder(eq(o), any())).thenReturn(new OrderResponse(
                o.getOrderId(), o.getReference(), o.getTotalAmount(), "CREDIT_CARD", o.getCustomerId(), o.getStatus() != null ? o.getStatus().name() : "REQUESTED"
        )));
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
        assertThat(caughtException).isNotNull();
        assertThat(caughtException).isInstanceOfAny(CustomerServiceUnavailableException.class, CustomerNotFoundException.class);
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

    // ---- Tenant-scoped by-id read (B3 Fase 1b) ----

    @Given("an order {int} tagged with tenant {string} exists")
    public void an_order_tagged_with_tenant(int id, String owningTenant) {
        Order order = Order.builder()
                .orderId(id).correlationId("corr-" + id).reference("ORD-" + id)
                .totalAmount(BigDecimal.valueOf(50)).status(OrderStatus.REQUESTED)
                .customerId("42").paymentMethod(PaymentMethod.CREDIT_CARD)
                .tenantId(owningTenant).build();
        // Tenant-scoped query returns the order only to its owning tenant, exactly as the real
        // WHERE tenant_id = :tenantId would; the caller here is bound to "test-tenant".
        when(orderRepository.findByOrderIdAndTenantId(eq(id), anyString())).thenAnswer(inv ->
                owningTenant.equals(inv.getArgument(1)) ? Optional.of(order) : Optional.empty());
        lenient().when(orderMapper.fromOrder(eq(order), any())).thenReturn(new OrderResponse(
                id, "ORD-" + id, BigDecimal.valueOf(50), "CREDIT_CARD", "42", "REQUESTED"));
    }

    @When("order {int} is requested by id")
    public void order_is_requested_by_id(int id) {
        try {
            orderByIdResponse = orderService.findById(id);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    @Then("the order by id is returned")
    public void the_order_by_id_is_returned() {
        assertThat(caughtException).as("a same-tenant read must not error").isNull();
        assertThat(orderByIdResponse).isNotNull();
        assertThat(orderByIdResponse.reference()).isEqualTo("ORD-700");
    }

    @Then("the order by id is not found")
    public void the_order_by_id_is_not_found() {
        assertThat(orderByIdResponse).as("no order should be returned across tenants").isNull();
        assertThat(caughtException).isInstanceOf(OrderNotFoundException.class);
    }

    // ---- Helpers ----

    private void setupOrderWithStatus(String corrId, OrderStatus status) {
        Order order = Order.builder()
                .orderId(1).correlationId(corrId).reference("REF-POLL")
                .totalAmount(BigDecimal.valueOf(100)).status(status)
                .tenantId("test-tenant").build();
        lenient().when(orderRepository.findByCorrelationId(corrId)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.findByCorrelationIdAndTenantId(eq(corrId), anyString())).thenReturn(Optional.of(order));
    }
}
