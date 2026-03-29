package code.with.vanilson.orderservice;

import org.springframework.stereotype.Component;

/**
 * OrderMapper — Application Layer (Phase 3 update)
 * <p>
 * Maps Order entity ↔ request/response DTOs.
 * Phase 3 change: toOrder() now accepts correlationId + status from OrderService.
 * These fields are set by OrderService before persisting — mapper stays pure (SRP).
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@Component
public class OrderMapper {

    /**
     * Maps an OrderRequest to an Order entity.
     * correlationId and status are intentionally NOT set here —
     * they are set by OrderService.persistOrderWithLines() before calling save().
     */
    public Order toOrder(OrderRequest request) {
        if (request == null) return null;
        return Order.builder()
                .orderId(request.id())
                .reference(request.reference())
                .totalAmount(request.amount())
                .paymentMethod(request.paymentMethod())
                .customerId(request.customerId())
                .status(OrderStatus.REQUESTED)
                .build();
    }

    /**
     * Maps an Order entity to an OrderResponse DTO.
     * paymentMethod serialised to String — decouples presentation from domain enum.
     */
    public OrderResponse fromOrder(Order order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getReference(),
                order.getTotalAmount(),
                order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null,
                order.getCustomerId()
        );
    }
}
