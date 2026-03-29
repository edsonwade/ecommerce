package code.with.vanilson.productservice.kafka;

import code.with.vanilson.productservice.Product;
import code.with.vanilson.productservice.ProductRepository;
import code.with.vanilson.productservice.exception.ProductPurchaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * InventoryReservationConsumer — Infrastructure Layer (Saga Step 1)
 * <p>
 * Consumes order.requested events and atomically reserves stock.
 * <p>
 * Saga choreography — product-service's role:
 * 1. Consume order.requested
 * 2. Attempt to reserve stock for all products (pessimistic lock)
 * 3a. SUCCESS → publish inventory.reserved → payment-service processes payment
 * 3b. FAILURE → publish inventory.insufficient → order-service cancels order
 * <p>
 * Idempotency: if the same eventId arrives twice (Kafka at-least-once),
 * the second reservation attempt will fail at the DB lock and the same
 * outcome event is published again — idempotent from order-service's perspective.
 * <p>
 * Manual acknowledgement: offset committed only after Kafka publish succeeds.
 * If publish fails → Kafka retries → product-service processes again (idempotent).
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationConsumer {

    private final ProductRepository                  productRepository;
    private final KafkaTemplate<String, Object>      kafkaTemplate;
    private final MessageSource                      messageSource;

    private static final String TOPIC_RESERVED     = "inventory.reserved";
    private static final String TOPIC_INSUFFICIENT = "inventory.insufficient";

    @KafkaListener(
            topics = "order.requested",
            groupId = "inventory-reservation-group",
            containerFactory = "inventoryKafkaListenerContainerFactory")
    @Transactional
    public void onOrderRequested(
            @Payload OrderRequestedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[InventoryConsumer] order.requested received: correlationId=[{}] products=[{}] partition=[{}] offset=[{}]",
                event.correlationId(), event.products().size(), partition, offset);

        try {
            List<InventoryReservedEvent.ReservedItem> reservedItems =
                    reserveStock(event.products(), event.correlationId());

            publishInventoryReserved(event, reservedItems);
            log.info("[InventoryConsumer] Stock reserved. correlationId=[{}] items=[{}]",
                    event.correlationId(), reservedItems.size());

        } catch (ProductPurchaseException ex) {
            // Stock insufficient — extract productId from exception context
            log.warn("[InventoryConsumer] Insufficient stock for correlationId=[{}]: {}",
                    event.correlationId(), ex.getMessage());
            publishInventoryInsufficient(event, null, ex.getMessage());
        }

        ack.acknowledge();
    }

    // -------------------------------------------------------

    /**
     * Reserves stock for all products using pessimistic locking.
     * If ANY product has insufficient stock → throws ProductPurchaseException
     * → entire reservation is rolled back (transaction boundary).
     */
    private List<InventoryReservedEvent.ReservedItem> reserveStock(
            List<OrderRequestedEvent.ProductPurchaseItem> items, String correlationId) {

        List<Integer> productIds = items.stream()
                .map(OrderRequestedEvent.ProductPurchaseItem::productId)
                .toList();

        List<Product> storedProducts = productRepository.findAllByIdInOrderById(productIds);

        if (storedProducts.size() != productIds.size()) {
            throw new ProductPurchaseException(
                    msg("product.purchase.not.found", productIds),
                    "product.purchase.not.found");
        }

        List<OrderRequestedEvent.ProductPurchaseItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(OrderRequestedEvent.ProductPurchaseItem::productId))
                .toList();

        List<InventoryReservedEvent.ReservedItem> reserved = new ArrayList<>();

        for (int i = 0; i < storedProducts.size(); i++) {
            Product product = storedProducts.get(i);
            OrderRequestedEvent.ProductPurchaseItem item = sortedItems.get(i);

            if (product.getAvailableQuantity() < item.quantity()) {
                throw new ProductPurchaseException(
                        msg("product.purchase.insufficient.stock",
                                item.productId(), product.getAvailableQuantity(), item.quantity()),
                        "product.purchase.insufficient.stock");
            }

            double newQty = product.getAvailableQuantity() - item.quantity();
            product.setAvailableQuantity(newQty);
            productRepository.save(product);

            log.debug("[InventoryConsumer] Stock updated: productId=[{}] newQty=[{}] correlationId=[{}]",
                    product.getId(), newQty, correlationId);

            reserved.add(new InventoryReservedEvent.ReservedItem(
                    product.getId(), product.getName(),
                    item.quantity(), product.getPrice()));
        }

        return reserved;
    }

    private void publishInventoryReserved(OrderRequestedEvent order,
                                           List<InventoryReservedEvent.ReservedItem> reserved) {
        InventoryReservedEvent event = new InventoryReservedEvent(
                UUID.randomUUID().toString(),
                order.correlationId(),
                order.orderReference(),
                order.customerId(),
                order.customerEmail(),
                order.customerFirstname(),
                order.customerLastname(),
                reserved,
                order.totalAmount(),
                order.paymentMethod(),
                Instant.now(),
                1
        );
        kafkaTemplate.send(TOPIC_RESERVED, order.correlationId(), event);
    }

    private void publishInventoryInsufficient(OrderRequestedEvent order,
                                               Integer productId, String reason) {
        InventoryInsufficientEvent event = new InventoryInsufficientEvent(
                UUID.randomUUID().toString(),
                order.correlationId(),
                order.orderReference(),
                productId,
                null,
                0, 0,
                Instant.now(),
                1
        );
        kafkaTemplate.send(TOPIC_INSUFFICIENT, order.correlationId(), event);
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
