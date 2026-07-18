package code.with.vanilson.orderservice.bdd;

import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.internal.InternalPurchaseController;
import code.with.vanilson.orderservice.internal.PurchaseExistsResponse;
import code.with.vanilson.orderservice.orderLine.OrderLineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * BDD step definitions for the F7 internal purchase-verification endpoint.
 * <p>
 * Wires a REAL {@link InternalPurchaseController} over a mocked {@link OrderLineRepository}, so the
 * scenarios exercise the actual behaviour: the controller passes the fulfilled-status set
 * (CONFIRMED/SHIPPED/DELIVERED) to the repository, and only a matching line in one of those states
 * makes the customer a verified buyer. POJO + Mockito glue (no Spring context) — matches the
 * order-service BDD convention.
 */
public class InternalPurchaseVerificationStepDefinitions {

    /** A recorded order line: which customer bought which product, and in what order state. */
    private record RecordedLine(String customerId, int productId, OrderStatus status) {
    }

    private final List<RecordedLine> lines = new ArrayList<>();
    private OrderLineRepository orderLineRepository;
    private InternalPurchaseController controller;
    private boolean result;

    @Before
    public void setUp() {
        lines.clear();
        orderLineRepository = Mockito.mock(OrderLineRepository.class);
        controller = new InternalPurchaseController(orderLineRepository);

        // Mirror the real query: true iff a recorded line matches customer + product AND its order
        // state is within the fulfilled set the controller passed in.
        when(orderLineRepository.existsPurchasedProduct(anyString(), anyInt(), any()))
                .thenAnswer(inv -> {
                    String customerId = inv.getArgument(0);
                    Integer productId = inv.getArgument(1);
                    Collection<OrderStatus> statuses = inv.getArgument(2);
                    return lines.stream().anyMatch(l ->
                            l.customerId().equals(customerId)
                                    && l.productId() == productId
                                    && statuses.contains(l.status()));
                });
    }

    @Given("customer {string} has a {string} order line for product {int}")
    public void customer_has_order_line(String customerId, String status, Integer productId) {
        lines.add(new RecordedLine(customerId, productId, OrderStatus.valueOf(status)));
    }

    @When("product-service checks whether customer {string} purchased product {int}")
    public void product_service_checks(String customerId, Integer productId) {
        ResponseEntity<PurchaseExistsResponse> response = controller.hasPurchased(customerId, productId);
        result = response.getBody() != null && response.getBody().purchased();
    }

    @Then("the purchase verification result is {word}")
    public void the_result_is(String expected) {
        assertThat(result).isEqualTo(Boolean.parseBoolean(expected));
    }
}
