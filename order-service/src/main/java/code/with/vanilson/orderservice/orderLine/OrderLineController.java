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
     *
     * @param orderId the parent order ID
     * @return list of order lines
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{order-id}")
    public ResponseEntity<List<OrderLineResponse>> findByOrderId(
            @PathVariable("order-id") Integer orderId) {
        return ResponseEntity.ok(orderLineService.findAllByOrderId(orderId));
    }
}
