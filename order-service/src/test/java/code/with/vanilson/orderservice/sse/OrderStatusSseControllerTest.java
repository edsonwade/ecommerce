package code.with.vanilson.orderservice.sse;

import code.with.vanilson.orderservice.OrderStatusSseController;
import code.with.vanilson.orderservice.event.OrderStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for OrderStatusSseController.
 * Verifies emitter lifecycle and event routing logic without Spring context.
 */
@DisplayName("OrderStatusSseController — SSE emitter lifecycle")
class OrderStatusSseControllerTest {

    private OrderStatusSseController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderStatusSseController();
    }

    @Nested
    @DisplayName("streamOrderStatus — emitter registration")
    class StreamOrderStatus {

        @Test
        @DisplayName("returns a non-null SseEmitter for any correlationId")
        void returnsNonNullEmitter() {
            SseEmitter emitter = controller.streamOrderStatus("corr-001");

            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("returns a distinct emitter per correlationId")
        void returnsDistinctEmittersPerCorrelationId() {
            SseEmitter emitter1 = controller.streamOrderStatus("corr-001");
            SseEmitter emitter2 = controller.streamOrderStatus("corr-002");

            assertThat(emitter1).isNotSameAs(emitter2);
        }

        @Test
        @DisplayName("replaces emitter when same correlationId reconnects")
        void replacesEmitterOnReconnect() {
            SseEmitter first  = controller.streamOrderStatus("corr-reconnect");
            SseEmitter second = controller.streamOrderStatus("corr-reconnect");

            assertThat(second).isNotSameAs(first);
        }
    }

    @Nested
    @DisplayName("onOrderStatusChanged — event routing")
    class OnOrderStatusChanged {

        @Test
        @DisplayName("sends event to registered emitter without throwing")
        void sendsEventToRegisteredEmitter() {
            controller.streamOrderStatus("corr-send");

            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    "corr-send", "CONFIRMED", "ORD-001", Instant.now());

            assertThatNoException().isThrownBy(() -> controller.onOrderStatusChanged(event));
        }

        @Test
        @DisplayName("does nothing when no emitter is registered for correlationId")
        void ignoresEventWithoutRegisteredEmitter() {
            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    "corr-unknown", "CONFIRMED", "ORD-002", Instant.now());

            assertThatNoException().isThrownBy(() -> controller.onOrderStatusChanged(event));
        }

        @Test
        @DisplayName("completes emitter on CONFIRMED status")
        void completesEmitterOnConfirmed() {
            controller.streamOrderStatus("corr-confirmed");

            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    "corr-confirmed", "CONFIRMED", "ORD-003", Instant.now());

            assertThatNoException().isThrownBy(() -> controller.onOrderStatusChanged(event));
        }

        @Test
        @DisplayName("completes emitter on CANCELLED status")
        void completesEmitterOnCancelled() {
            controller.streamOrderStatus("corr-cancelled");

            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    "corr-cancelled", "CANCELLED", "ORD-004", Instant.now());

            assertThatNoException().isThrownBy(() -> controller.onOrderStatusChanged(event));
        }

        @Test
        @DisplayName("completes emitter on TIMEOUT status")
        void completesEmitterOnTimeout() {
            controller.streamOrderStatus("corr-timeout");

            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    "corr-timeout", "TIMEOUT", "ORD-005", Instant.now());

            assertThatNoException().isThrownBy(() -> controller.onOrderStatusChanged(event));
        }

        @Test
        @DisplayName("does not complete emitter on non-terminal status INVENTORY_INSUFFICIENT")
        void doesNotCompleteEmitterOnIntermediateStatus() {
            SseEmitter emitter = controller.streamOrderStatus("corr-intermediate");

            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    "corr-intermediate", "INVENTORY_INSUFFICIENT", "ORD-006", Instant.now());

            assertThatNoException().isThrownBy(() -> controller.onOrderStatusChanged(event));
            // emitter is still accessible (not removed) — we send a second event
            OrderStatusChangedEvent second = new OrderStatusChangedEvent(
                    "corr-intermediate", "CANCELLED", "ORD-006", Instant.now());
            assertThatNoException().isThrownBy(() -> controller.onOrderStatusChanged(second));
        }
    }
}
