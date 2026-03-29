package code.with.vanilson.notification.kafka.order;

import java.math.BigDecimal;
import java.util.List;

/**
 * OrderConfirmation — Local Kafka event DTO (notification-service)
 * <p>
 * Represents the order confirmation payload consumed from 'order-topic'.
 * Owned exclusively by notification-service — no import from order-service JAR.
 * Field names must match the JSON serialised by order-service's OrderConfirmation record.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        String paymentMethod,
        CustomerSummary customer,
        List<ProductSummary> products
) {

    /** Nested DTO — represents customer data in the event payload. */
    public record CustomerSummary(
            String customerId,
            String firstname,
            String lastname,
            String email
    ) {}

    /** Nested DTO — represents a product line in the order confirmation. */
    public record ProductSummary(
            Integer productId,
            String name,
            String description,
            java.math.BigDecimal price,
            double quantity
    ) {}
}
