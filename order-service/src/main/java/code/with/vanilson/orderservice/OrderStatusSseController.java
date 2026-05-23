package code.with.vanilson.orderservice;

import code.with.vanilson.orderservice.event.OrderStatusChangedEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OrderStatusSseController — Presentation Layer (Phase 3)
 * <p>
 * Streams real-time order status updates to clients using Server-Sent Events.
 * Clients connect once after receiving 202 Accepted from POST /orders and receive
 * push notifications as the saga progresses — no polling needed.
 * <p>
 * Polling (GET /orders/status/{correlationId}) remains as fallback for environments
 * where SSE is blocked (corporate proxies, older browsers).
 * <p>
 * Scaling note: the ConcurrentHashMap is single-instance only.
 * For multi-instance deployment, use Redis Pub/Sub to broadcast events across instances.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order SSE API", description = "Real-time order status streaming via Server-Sent Events")
public class OrderStatusSseController {

    private static final long SSE_TIMEOUT_MS = 300_000L; // 5 minutes
    private static final Set<String> TERMINAL_STATUSES = Set.of("CONFIRMED", "CANCELLED", "TIMEOUT");

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Operation(
            summary = "Stream order status updates (SSE)",
            description = """
                    Opens a Server-Sent Event stream for the given correlationId.
                    Receives 'status-update' events as the saga progresses.
                    Stream closes automatically on terminal states: CONFIRMED, CANCELLED, TIMEOUT.
                    If SSE is unavailable, fall back to polling GET /api/v1/orders/status/{correlationId}.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/{correlationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderStatus(@PathVariable String correlationId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(correlationId, emitter);

        emitter.onCompletion(() -> {
            emitters.remove(correlationId);
            log.debug("[SSE] Emitter completed for correlationId=[{}]", correlationId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(correlationId);
            log.debug("[SSE] Emitter timed out for correlationId=[{}]", correlationId);
        });
        emitter.onError(ex -> {
            emitters.remove(correlationId);
            log.debug("[SSE] Emitter error for correlationId=[{}]: {}", correlationId, ex.getMessage());
        });

        log.debug("[SSE] Emitter registered for correlationId=[{}]", correlationId);
        return emitter;
    }

    /**
     * Receives intra-JVM Spring events from OrderSagaConsumer and SagaTimeoutScheduler
     * and forwards them to the connected SSE client (if any).
     */
    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        SseEmitter emitter = emitters.get(event.correlationId());
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("status-update")
                    .data(event));

            if (TERMINAL_STATUSES.contains(event.status())) {
                emitter.complete();
            }
        } catch (IOException e) {
            emitters.remove(event.correlationId());
            log.debug("[SSE] Write failed for correlationId=[{}]: {}", event.correlationId(), e.getMessage());
        }
    }
}
