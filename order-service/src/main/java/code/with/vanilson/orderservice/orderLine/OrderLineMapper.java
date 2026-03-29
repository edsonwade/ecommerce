package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.orderservice.Order;
import org.springframework.stereotype.Component;

/**
 * OrderLineMapper — Application Layer
 * <p>
 * Maps between OrderLineRequest DTO and OrderLine entity, and vice versa.
 * Single Responsibility (SOLID-S): pure mapping logic, nothing else.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Component
public class OrderLineMapper {

    /**
     * Maps an OrderLineRequest to an OrderLine entity.
     * Creates an Order reference using only the orderId (no full fetch needed).
     *
     * @param request the order line creation request
     * @return OrderLine entity ready to be persisted
     */
    public OrderLine toOrderLine(OrderLineRequest request) {
        return OrderLine.builder()
                .id(request.id())
                .order(Order.builder()
                        .orderId(request.orderId())
                        .build())
                .productId(request.productId())
                .quantity(request.quantity())
                .build();
    }

    /**
     * Maps an OrderLine entity to an OrderLineResponse DTO.
     *
     * @param orderLine the persisted OrderLine entity
     * @return OrderLineResponse DTO for the HTTP response
     */
    public OrderLineResponse toOrderLineResponse(OrderLine orderLine) {
        return new OrderLineResponse(
                orderLine.getId(),
                orderLine.getQuantity()
        );
    }
}
