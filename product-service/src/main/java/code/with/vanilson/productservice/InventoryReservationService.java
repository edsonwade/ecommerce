package code.with.vanilson.productservice;

import code.with.vanilson.productservice.exception.ProductPurchaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * InventoryReservationService — Application Layer (shared reservation core)
 * <p>
 * Single implementation of the stock reservation business logic, used by BOTH:
 * - the sync HTTP path ({@link ProductService#purchaseProducts}) and
 * - the async Kafka saga path (InventoryReservationConsumer).
 * <p>
 * Previously each path duplicated fetch-lock / validate / deduct logic, so a fix
 * in one path silently missed the other. Both callers are now thin wrappers that
 * add their own side effects (cache eviction + DTO mapping vs. reservation rows
 * + saga events).
 * <p>
 * Concurrency: products are fetched with a pessimistic write lock
 * (SELECT FOR UPDATE, see ProductRepository.findAllByIdInOrderById) inside the
 * caller's transaction — Propagation.MANDATORY enforces that a transaction exists.
 * If ANY product is missing or short on stock the whole reservation rolls back.
 * </p>
 *
 * @author vamuhong
 * @version 3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final ProductRepository productRepository;
    private final MessageSource     messageSource;

    /** Neutral input line — both ProductPurchaseRequest (HTTP) and
     *  OrderRequestedEvent.ProductPurchaseItem (Kafka) map onto it. */
    public record ReservationItem(Integer productId, double quantity) {}

    /** Reserved product with the quantity that was deducted. */
    public record ReservedLine(Product product, double quantity) {}

    /**
     * Atomically reserves stock for all items, or throws and rolls back.
     *
     * @throws ProductPurchaseException if any product is missing
     *         (key product.purchase.not.found) or short on stock
     *         (key product.purchase.insufficient.stock — carries the failing
     *         product's id, name, requested and available quantities)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReservedLine> reserveStock(List<ReservationItem> items) {
        List<Integer> productIds = items.stream()
                .map(ReservationItem::productId)
                .toList();

        List<Product> storedProducts = productRepository.findAllByIdInOrderById(productIds);

        if (storedProducts.size() != productIds.size()) {
            List<Integer> foundIds = storedProducts.stream().map(Product::getId).toList();
            List<Integer> missingIds = productIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new ProductPurchaseException(
                    msg("product.purchase.not.found", missingIds.toString()),
                    "product.purchase.not.found");
        }

        List<ReservationItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(ReservationItem::productId))
                .toList();

        List<ReservedLine> reserved = new ArrayList<>();

        for (int i = 0; i < storedProducts.size(); i++) {
            Product product = storedProducts.get(i);
            ReservationItem item = sortedItems.get(i);

            log.info(msg("product.log.purchase.item",
                    product.getId(), item.quantity(), product.getAvailableQuantity()));

            if (product.getAvailableQuantity() < item.quantity()) {
                throw new ProductPurchaseException(
                        msg("product.purchase.insufficient.stock",
                                item.productId(), product.getAvailableQuantity(), item.quantity()),
                        "product.purchase.insufficient.stock",
                        product.getId(), product.getName(),
                        item.quantity(), product.getAvailableQuantity());
            }

            double newQty = product.getAvailableQuantity() - item.quantity();
            product.setAvailableQuantity(newQty);
            productRepository.save(product);

            log.info(msg("product.log.stock.updated", product.getId(), newQty));
            reserved.add(new ReservedLine(product, item.quantity()));
        }

        return reserved;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
