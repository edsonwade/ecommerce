package code.with.vanilson.orderservice;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * OrderController — Presentation Layer (Phase 3)
 * <p>
 * Phase 3 change: POST /orders returns 202 Accepted + correlationId
 * instead of 201 Created + orderId.
 * <p>
 * 202 Accepted = "I received your request and will process it.
 * Poll the status endpoint to track progress."
 * <p>
 * New endpoint: GET /orders/status/{correlationId}
 * Client polls this after receiving 202 to track saga progress.
 * </p>
 *
 * @author vamuhong
 * @version 3.0
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order API", description = "Async order management — event-driven saga (Phase 3)")
public class OrderController {

    private final OrderService orderService;

    @Operation(
            summary = "Create a new order (async)",
            description = """
                    Accepts the order request, validates the customer, persists the order,
                    and immediately returns 202 Accepted with a correlationId.
                    The order processing continues asynchronously via Kafka saga:
                    inventory reservation → payment → confirmation.
                    Poll GET /orders/status/{correlationId} to track progress.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Order accepted — processing asynchronously"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "503", description = "Customer service unavailable"),
            @ApiResponse(responseCode = "409", description = "Duplicate order reference")
    })
    @PostMapping
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody @Valid OrderRequest request) {
        String correlationId = orderService.createOrder(request);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "correlationId", correlationId,
                        "status", "REQUESTED",
                        "message", "Order accepted. Poll /api/v1/orders/status/" + correlationId
                ));
    }

    @Operation(
            summary = "Poll order status by correlationId",
            description = "Returns the current saga status of an order. " +
                    "Poll this after receiving 202 from POST /orders.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status returned"),
            @ApiResponse(responseCode = "404", description = "Order not found for correlationId")
    })
    @GetMapping("/status/{correlationId}")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(
            @PathVariable String correlationId) {
        return ResponseEntity.ok(orderService.getOrderStatus(correlationId));
    }

    @Operation(summary = "List all orders")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> findAll() {
        return ResponseEntity.ok(orderService.findAllOrders());
    }

    @Operation(summary = "Get order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{order-id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable("order-id") Integer orderId) {
        return ResponseEntity.ok(orderService.findById(orderId));
    }
}
