package code.with.vanilson.orderservice;

import jakarta.validation.constraints.NotNull;

/**
 * UpdateOrderStatusRequest — Presentation DTO (Fase 5, fulfillment).
 * <p>
 * Body of {@code PATCH /api/v1/orders/{order-id}/status}. Only the fulfillment states
 * {@code SHIPPED} / {@code DELIVERED} are accepted through this endpoint — the whitelist is
 * enforced in {@link OrderService#updateStatusManually}. An unknown enum name (e.g.
 * {@code "PAUSED"}) fails Jackson binding and is mapped to {@code 400 order.status.invalid}
 * by the global handler; a missing value fails {@code @NotNull} → {@code 400}.
 *
 * @author vamuhong
 * @version 1.0
 */
public record UpdateOrderStatusRequest(
        @NotNull(message = "{order.status.required}") OrderStatus status) {
}
