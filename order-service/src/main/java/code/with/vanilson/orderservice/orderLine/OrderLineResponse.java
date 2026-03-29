package code.with.vanilson.orderservice.orderLine;

/**
 * OrderLineResponse — Presentation Layer DTO
 * <p>
 * Immutable response record for an order line item.
 * Returned by the OrderLineController to the client.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public record OrderLineResponse(
        Integer id,
        double quantity
) {
}
