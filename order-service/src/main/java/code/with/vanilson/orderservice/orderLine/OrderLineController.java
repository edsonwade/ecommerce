package code.with.vanilson.orderservice.orderLine;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OrderLineController — Presentation Layer
 * <p>
 * Exposes order line queries for a given order.
 * Single Responsibility (SOLID-S): only handles HTTP concerns for order lines.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@RestController
@RequestMapping("/api/v1/order-lines")
@RequiredArgsConstructor
public class OrderLineController {

    private final OrderLineService orderLineService;

    /**
     * Returns all order lines for a given order ID.
     * <p>
     * Any authenticated user may call this, but the service enforces that only the
     * order's owner (or an ADMIN) actually receives the lines — non-owners get 403.
     * This matches {@code GET /api/v1/orders/{id}} and the error message contract
     * ("Only the owner or an ADMIN can view it"). Previously this was
     * {@code hasRole('ADMIN')}, which 403'd every customer viewing their own order.
     *
     * @param orderId the parent order ID
     * @return list of order lines
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{order-id}")
    public ResponseEntity<List<OrderLineResponse>> findByOrderId(
            @PathVariable("order-id") Integer orderId) {
        return ResponseEntity.ok(orderLineService.findAllByOrderId(orderId));
    }
}
