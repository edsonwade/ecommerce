package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import code.with.vanilson.productservice.kafka.OrderRefundedEvent;
import code.with.vanilson.productservice.kafka.RefundRestockConsumer;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mockito;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefundRestockSteps — BDD Step Definitions (Fase 6).
 * <p>
 * POJO + Mockito, like the sibling step classes in this glue package. The REAL
 * {@link RefundRestockConsumer} runs against mocked repositories, exercising the same
 * restock logic as production without a broker or database.
 */
public class RefundRestockSteps {

    private static final String CORRELATION_ID = "corr-bdd-refund-001";
    private static final Integer PRODUCT_ID = 1;

    private ProductRepository productRepository;
    private InventoryReservationRepository reservationRepository;
    private RefundRestockConsumer consumer;
    private Acknowledgment acknowledgment;

    private Product product;
    private InventoryReservation reservation;

    @Before
    public void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        reservationRepository = Mockito.mock(InventoryReservationRepository.class);
        acknowledgment = Mockito.mock(Acknowledgment.class);

        consumer = new RefundRestockConsumer(reservationRepository, productRepository, new SimpleMeterRegistry());

        product = new Product(PRODUCT_ID, "Laptop", "Gaming Laptop", 10.0, BigDecimal.valueOf(1200));
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Given("a reserved product with quantity {int} for the refund correlation")
    public void a_reserved_product_with_quantity(int quantity) {
        reservation = InventoryReservation.builder()
                .id(1L).correlationId(CORRELATION_ID).productId(PRODUCT_ID)
                .reservedQuantity(quantity).status(InventoryReservation.ReservationStatus.RESERVED)
                .createdAt(LocalDateTime.now()).build();
        when(reservationRepository.findByCorrelationIdAndStatus(
                CORRELATION_ID, InventoryReservation.ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        lenient().when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Given("no RESERVED records exist for the refund correlation")
    public void no_reserved_records_exist() {
        when(reservationRepository.findByCorrelationIdAndStatus(
                CORRELATION_ID, InventoryReservation.ReservationStatus.RESERVED))
                .thenReturn(List.of());
    }

    @When("the order.refunded event is consumed")
    public void the_order_refunded_event_is_consumed() {
        OrderRefundedEvent event = new OrderRefundedEvent(
                "evt-bdd-refund-001", CORRELATION_ID, "ORD-BDD-REFUND-001", Instant.now(), 1);
        consumer.onOrderRefunded(event, 0, 0L, acknowledgment);
    }

    @Then("the product stock is restored by {int}")
    public void the_product_stock_is_restored_by(int quantity) {
        assertThat(product.getAvailableQuantity()).isEqualTo(10.0 + quantity);
        verify(acknowledgment).acknowledge();
    }

    @Then("the reservation is marked RELEASED")
    public void the_reservation_is_marked_released() {
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isNotNull();
    }

    @Then("the product stock is not touched")
    public void the_product_stock_is_not_touched() {
        verify(productRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }
}
