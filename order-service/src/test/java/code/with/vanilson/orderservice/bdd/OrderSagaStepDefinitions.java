package code.with.vanilson.orderservice.bdd;

import code.with.vanilson.orderservice.OrderService;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.kafka.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OrderSagaStepDefinitions — BDD step definitions for saga event processing (Phase 0)
 * <p>
 * Verifies OrderSagaConsumer behavior via Cucumber scenarios:
 * - payment.authorized → CONFIRMED + OrderConfirmation sent via OrderProducer
 * - payment.failed → CANCELLED + no notification sent
 * - inventory.insufficient → INVENTORY_INSUFFICIENT → CANCELLED
 * - Notification failure is isolated: order still confirmed even if OrderProducer fails
 * </p>
 */
public class OrderSagaStepDefinitions {

    private OrderService      orderService;
    private OrderProducer     orderProducer;
    private MessageSource     messageSource;
    private Acknowledgment    acknowledgment;
    private OrderSagaConsumer consumer;

    private String correlationId;

    @Before("@saga")
    public void setUp() {
        orderService   = Mockito.mock(OrderService.class);
        orderProducer  = Mockito.mock(OrderProducer.class);
        messageSource  = Mockito.mock(MessageSource.class);
        acknowledgment = Mockito.mock(Acknowledgment.class);

        consumer = new OrderSagaConsumer(orderService, orderProducer, messageSource);

        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
    }

    // ─── Given ────────────────────────────────────────────────────────────

    @Given("a correlation ID {string} is tracked")
    public void aCorrelationIdIsTracked(String corrId) {
        this.correlationId = corrId;
    }

    @Given("the OrderProducer throws an exception on sendOrderConfirmation")
    public void orderProducerThrowsOnSend() {
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(orderProducer).sendOrderConfirmation(any());
    }

    // ─── When ─────────────────────────────────────────────────────────────

    @When("a payment.authorized event is received for {string}")
    public void paymentAuthorizedReceived(String corrId) {
        PaymentAuthorizedEvent event = new PaymentAuthorizedEvent(
                "evt-bdd-001", corrId, "ORD-BDD-001",
                42, "cust-001", "ana@example.com", "Ana", "Silva",
                List.of(new PaymentAuthorizedEvent.ReservedItem(1, "Laptop", 2.0, BigDecimal.valueOf(1200))),
                BigDecimal.valueOf(2400), "CREDIT_CARD",
                Instant.now(), 2);
        consumer.onPaymentAuthorized(event, 0, 0L, acknowledgment);
    }

    @When("a payment.failed event is received for {string}")
    public void paymentFailedReceived(String corrId) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                "evt-bdd-fail-001", corrId, "ORD-BDD-001",
                "Insufficient funds", Instant.now(), 1);
        consumer.onPaymentFailed(event, 0, 0L, acknowledgment);
    }

    @When("an inventory.insufficient event is received for {string}")
    public void inventoryInsufficientReceived(String corrId) {
        InventoryInsufficientEvent event = new InventoryInsufficientEvent(
                "evt-bdd-inv-001", corrId, "ORD-BDD-001",
                1, 5.0, 0.0, Instant.now(), 1);
        consumer.onInventoryInsufficient(event, 0, 0L, acknowledgment);
    }

    // ─── Then ─────────────────────────────────────────────────────────────

    @Then("the order status is updated to CONFIRMED")
    public void orderStatusUpdatedToConfirmed() {
        verify(orderService).updateStatus(correlationId, OrderStatus.CONFIRMED);
    }

    @Then("the order status is updated to CANCELLED")
    public void orderStatusUpdatedToCancelled() {
        verify(orderService).updateStatus(correlationId, OrderStatus.CANCELLED);
    }

    @Then("the order status is updated to INVENTORY_INSUFFICIENT")
    public void orderStatusUpdatedToInventoryInsufficient() {
        verify(orderService).updateStatus(correlationId, OrderStatus.INVENTORY_INSUFFICIENT);
    }

    @Then("the order status is then updated to CANCELLED")
    public void orderStatusThenUpdatedToCancelled() {
        verify(orderService, times(2)).updateStatus(eq(correlationId), any(OrderStatus.class));
        verify(orderService).updateStatus(correlationId, OrderStatus.CANCELLED);
    }

    @Then("an OrderConfirmation notification is sent via OrderProducer")
    public void orderConfirmationSent() {
        verify(orderProducer).sendOrderConfirmation(any(OrderConfirmation.class));
    }

    @Then("no OrderConfirmation notification is sent")
    public void noOrderConfirmationSent() {
        verify(orderProducer, never()).sendOrderConfirmation(any());
    }

    @Then("the Kafka offset is acknowledged")
    public void kafkaOffsetAcknowledged() {
        verify(acknowledgment).acknowledge();
    }
}
