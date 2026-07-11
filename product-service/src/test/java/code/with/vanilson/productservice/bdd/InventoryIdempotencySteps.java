package code.with.vanilson.productservice.bdd;

import code.with.vanilson.productservice.InventoryReservationService;
import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.domain.InventoryReservation;
import code.with.vanilson.productservice.domain.InventoryReservationRepository;
import code.with.vanilson.productservice.kafka.InventoryReservationConsumer;
import code.with.vanilson.productservice.kafka.OrderRequestedEvent;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryIdempotencySteps — BDD Step Definitions (B4)
 * <p>
 * Implements inventory_reservation_idempotency.feature.
 * <p>
 * POJO + Mockito, like the sibling step classes in this glue package (this suite
 * runs with DefaultObjectFactory, so there is no Spring context here). The product
 * and reservation repositories are map/list-backed Mockito stubs; the REAL
 * InventoryReservationConsumer and InventoryReservationService run the actual
 * guard + fetch/validate/deduct logic, so the scenarios exercise the same
 * idempotency path as production — just without a broker or database.
 * </p>
 */
public class InventoryIdempotencySteps {

    private static final String CORRELATION_ID = "corr-bdd-idem-001";
    private static final String TOPIC_RESERVED = "inventory.reserved";

    /** In-memory catalog backing the product repository stub. */
    private final Map<Integer, Product> store = new LinkedHashMap<>();
    /** In-memory reservation rows backing the reservation repository stub. */
    private final List<InventoryReservation> reservationRows = new ArrayList<>();

    private InventoryReservationConsumer consumer;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private Acknowledgment acknowledgment;
    private OrderRequestedEvent lastEvent;

    @Before
    public void resetState() {
        // Hooks run for every scenario in the suite (Cucumber hooks are global to
        // the glue path), so this must stay dependency-free and side-effect safe.
        store.clear();
        reservationRows.clear();
        consumer = null;
        kafkaTemplate = null;
        acknowledgment = null;
        lastEvent = null;
    }

    @SuppressWarnings("unchecked")
    private void init() {
        ProductRepository productRepository = Mockito.mock(ProductRepository.class);
        InventoryReservationRepository reservationRepository =
                Mockito.mock(InventoryReservationRepository.class);
        MessageSource messageSource = Mockito.mock(MessageSource.class);
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        acknowledgment = Mockito.mock(Acknowledgment.class);

        lenient().when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Map-backed product repository: reserveStock() fetches with
        // findAllByIdInOrderById and persists deductions with save(); the duplicate
        // path rebuilds the outcome event with findAllById.
        lenient().when(productRepository.findAllByIdInOrderById(any())).thenAnswer(inv -> {
            List<Integer> ids = inv.getArgument(0);
            return ids.stream()
                    .distinct()
                    .map(store::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Product::getId))
                    .toList();
        });
        lenient().when(productRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Integer> ids = inv.getArgument(0);
            List<Product> found = new ArrayList<>();
            for (Integer id : ids) {
                Product p = store.get(id);
                if (p != null) {
                    found.add(p);
                }
            }
            return found;
        });
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            store.put(p.getId(), p);
            return p;
        });

        // List-backed reservation repository: the consumer writes rows on first
        // delivery and reads them back as the idempotency record on redelivery.
        lenient().when(reservationRepository.findByCorrelationId(any(String.class)))
                .thenAnswer(inv -> {
                    String correlationId = inv.getArgument(0);
                    return reservationRows.stream()
                            .filter(r -> r.getCorrelationId().equals(correlationId))
                            .toList();
                });
        lenient().when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(inv -> {
                    InventoryReservation r = inv.getArgument(0);
                    reservationRows.add(r);
                    return r;
                });

        // TransactionTemplate over a mocked manager runs the callback inline,
        // mirroring the production rollback semantics without a real datasource.
        PlatformTransactionManager transactionManager =
                Mockito.mock(PlatformTransactionManager.class);

        consumer = new InventoryReservationConsumer(
                new InventoryReservationService(productRepository, messageSource),
                reservationRepository, productRepository, kafkaTemplate,
                new SimpleMeterRegistry(),
                new TransactionTemplate(transactionManager));
    }

    // -------------------------------------------------------
    // Given
    // -------------------------------------------------------

    @Given("a reservation catalog with product {int} named {string} stocked at {int}")
    public void aReservationCatalogWithProduct(int productId, String name, int stock) {
        init();
        store.put(productId, Product.builder()
                .id(productId)
                .name(name)
                .description(name + " description")
                .availableQuantity(stock)
                .price(BigDecimal.valueOf(1200))
                .build());
    }

    @And("the order was already compensated with a released reservation of {int} units of product {int}")
    public void theOrderWasAlreadyCompensated(int quantity, int productId) {
        reservationRows.add(InventoryReservation.builder()
                .correlationId(CORRELATION_ID)
                .productId(productId)
                .reservedQuantity(quantity)
                .status(InventoryReservation.ReservationStatus.RELEASED)
                .createdAt(LocalDateTime.now())
                .releasedAt(LocalDateTime.now())
                .build());
    }

    // -------------------------------------------------------
    // When
    // -------------------------------------------------------

    @When("an order requested event for product {int} with quantity {int} is delivered")
    public void anOrderRequestedEventIsDelivered(int productId, int quantity) {
        lastEvent = new OrderRequestedEvent(
                "evt-bdd-001",
                CORRELATION_ID,
                "cust-001",
                "ana@example.com",
                "Ana",
                "Silva",
                List.of(new OrderRequestedEvent.ProductPurchaseItem(productId, quantity)),
                BigDecimal.valueOf(3600),
                "CREDIT_CARD",
                "ORD-BDD-001",
                "default",
                42,
                Instant.now(),
                1
        );
        consumer.onOrderRequested(lastEvent, 0, 0L, acknowledgment);
    }

    @And("the same order requested event is delivered again")
    public void theSameEventIsDeliveredAgain() {
        consumer.onOrderRequested(lastEvent, 0, 1L, acknowledgment);
    }

    // -------------------------------------------------------
    // Then
    // -------------------------------------------------------

    @Then("the reserved stock for product {int} should be {int}")
    public void theReservedStockShouldBe(int productId, int expectedStock) {
        Product product = store.get(productId);
        assertThat(product)
                .as("Product " + productId + " should exist in the catalog")
                .isNotNull();
        assertThat(product.getAvailableQuantity())
                .as("Available stock for product " + productId)
                .isEqualTo(expectedStock);
    }

    @And("only one reservation record should exist for the order")
    public void onlyOneReservationRecordShouldExist() {
        List<InventoryReservation> rows = reservationRows.stream()
                .filter(r -> r.getCorrelationId().equals(CORRELATION_ID))
                .toList();
        assertThat(rows)
                .as("A duplicate delivery must not add a second reservation row")
                .hasSize(1);
    }

    @And("the inventory reserved event should have been published {int} times")
    public void theInventoryReservedEventShouldHaveBeenPublished(int expectedTimes) {
        verify(kafkaTemplate, times(expectedTimes))
                .send(eq(TOPIC_RESERVED), eq(CORRELATION_ID), any());
    }
}
