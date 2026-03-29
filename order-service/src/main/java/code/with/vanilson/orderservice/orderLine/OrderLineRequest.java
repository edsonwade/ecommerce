package code.with.vanilson.orderservice.orderLine;

/**
 * OrderLineRequest — Application Layer DTO
 * <p>
 * Immutable request record for creating an order line.
 * Passed from OrderService to OrderLineService — stays within the service boundary.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record OrderLineRequest(
        Integer id,
        Integer orderId,
        Integer productId,
        double quantity
) {
}
