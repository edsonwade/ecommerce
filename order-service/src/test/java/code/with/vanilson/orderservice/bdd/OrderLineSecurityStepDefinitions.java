package code.with.vanilson.orderservice.bdd;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.orderLine.OrderLine;
import code.with.vanilson.orderservice.orderLine.OrderLineMapper;
import code.with.vanilson.orderservice.orderLine.OrderLineRepository;
import code.with.vanilson.orderservice.orderLine.OrderLineResponse;
import code.with.vanilson.orderservice.orderLine.OrderLineService;
import code.with.vanilson.orderservice.product.ProductClient;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
 * BDD step definitions for seller data-isolation on order-line reads.
 * <p>
 * Wires a REAL {@link OrderLineService} over mocked repositories so the scenarios exercise the
 * actual authorization-and-scoping branch ({@code findAllByOrderId}): ADMIN and the customer-owner
 * see the whole order, a SELLER sees ONLY their own lines, and a seller with no line in the order
 * is forbidden. Regression for the live bug where a seller saw another seller's / "system"-owned
 * products in an order they only partly fulfilled.
 * </p>
 */
public class OrderLineSecurityStepDefinitions {

    private OrderLineRepository orderLineRepository;
    private OrderLineMapper mapper;
    private OrderRepository orderRepository;
    private TenantHibernateFilterActivator filterActivator;
    private MessageSource messageSource;
    private ProductClient productClient;
    private OrderLineService orderLineService;

    // State
    private List<OrderLineResponse> result;
    private Exception caughtException;

    @Before
    public void setUp() {
        orderLineRepository = Mockito.mock(OrderLineRepository.class);
        mapper = Mockito.mock(OrderLineMapper.class);
        orderRepository = Mockito.mock(OrderRepository.class);
        filterActivator = Mockito.mock(TenantHibernateFilterActivator.class);
        messageSource = Mockito.mock(MessageSource.class);
        productClient = Mockito.mock(ProductClient.class);

        orderLineService = new OrderLineService(
                orderLineRepository, mapper, orderRepository, filterActivator, messageSource, productClient);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Map any line to a response preserving id + productId + quantity.
        lenient().when(mapper.toOrderLineResponse(any(OrderLine.class)))
                .thenAnswer(inv -> {
                    OrderLine ol = inv.getArgument(0);
                    return new OrderLineResponse(ol.getId(), ol.getProductId(), ol.getQuantity());
                });

        TenantContext.setCurrentTenantId("test-tenant");
        result = null;
        caughtException = null;
    }

    @After
    public void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---- Given ----

    @Given("order {int} owned by customer {string} has the lines:")
    public void order_has_the_lines(Integer orderId, String customerId, DataTable table) {
        Order order = Order.builder().orderId(orderId).customerId(customerId).tenantId("test-tenant").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        List<Map<String, String>> rows = table.asMaps();
        List<OrderLine> allLines = rows.stream().map(row -> OrderLine.builder()
                .id(Integer.parseInt(row.get("productId"))) // unique-enough id per row
                .order(order)
                .productId(Integer.parseInt(row.get("productId")))
                .quantity(1.0)
                .sellerId(row.get("sellerId"))
                .build()).toList();

        when(orderLineRepository.findAllOrderById(orderId)).thenReturn(allLines);

        // Mirror the repository's findByOrderIdAndSellerId: a seller sees only the lines they own
        // (sellers absent from the table own nothing → empty list → forbidden).
        when(orderLineRepository.findByOrderIdAndSellerId(org.mockito.ArgumentMatchers.eq(orderId), anyString()))
                .thenAnswer(inv -> {
                    String sid = inv.getArgument(1);
                    return allLines.stream().filter(l -> sid.equals(l.getSellerId())).toList();
                });
    }

    // ---- When ----

    @When("the seller {string} requests the lines of order {int}")
    public void seller_requests_lines(String sellerId, Integer orderId) {
        authenticateAs(Long.parseLong(sellerId), "SELLER");
        invoke(orderId);
    }

    @When("the customer {string} requests the lines of order {int}")
    public void customer_requests_lines(String customerId, Integer orderId) {
        authenticateAs(Long.parseLong(customerId), "USER");
        invoke(orderId);
    }

    @When("an admin requests the lines of order {int}")
    public void admin_requests_lines(Integer orderId) {
        authenticateAs(999L, "ADMIN");
        invoke(orderId);
    }

    // ---- Then ----

    @Then("exactly {int} order line is returned")
    public void exactly_n_order_line_is_returned(int count) {
        exactly_n_order_lines_are_returned(count);
    }

    @Then("exactly {int} order lines are returned")
    public void exactly_n_order_lines_are_returned(int count) {
        assertThat(caughtException).isNull();
        assertThat(result).isNotNull().hasSize(count);
    }

    @Then("the returned order line is for product {int}")
    public void the_returned_order_line_is_for_product(int productId) {
        assertThat(result).isNotNull()
                .extracting(OrderLineResponse::productId)
                .containsExactly(productId);
    }

    @Then("the order line request is forbidden")
    public void the_order_line_request_is_forbidden() {
        assertThat(caughtException).isInstanceOf(OrderForbiddenException.class);
    }

    // ---- Helpers ----

    private void invoke(Integer orderId) {
        try {
            result = orderLineService.findAllByOrderId(orderId);
        } catch (Exception e) {
            caughtException = e;
        }
    }

    private void authenticateAs(long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user@test.com", userId, "test-tenant", role),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
