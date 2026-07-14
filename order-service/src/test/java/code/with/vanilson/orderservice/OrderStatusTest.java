package code.with.vanilson.orderservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static code.with.vanilson.orderservice.OrderStatus.CANCELLED;
import static code.with.vanilson.orderservice.OrderStatus.CONFIRMED;
import static code.with.vanilson.orderservice.OrderStatus.DELIVERED;
import static code.with.vanilson.orderservice.OrderStatus.INVENTORY_INSUFFICIENT;
import static code.with.vanilson.orderservice.OrderStatus.INVENTORY_RESERVED;
import static code.with.vanilson.orderservice.OrderStatus.PAYMENT_AUTHORIZED;
import static code.with.vanilson.orderservice.OrderStatus.PAYMENT_FAILED;
import static code.with.vanilson.orderservice.OrderStatus.PENDING_PAYMENT;
import static code.with.vanilson.orderservice.OrderStatus.REFUNDED;
import static code.with.vanilson.orderservice.OrderStatus.REQUESTED;
import static code.with.vanilson.orderservice.OrderStatus.SHIPPED;
import static code.with.vanilson.orderservice.OrderStatus.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderStatusTest — Unit tests for the {@link OrderStatus#canTransitionTo} state machine (Fase 5).
 * <p>
 * Fase 5 extends the machine past confirmation: CONFIRMED stops being terminal and gains a
 * fulfillment chain (SHIPPED → DELIVERED) plus a refund exit (→ REFUNDED) reachable from any
 * post-confirmation state. These tests pin BOTH the new transitions AND — critically — that the
 * pre-existing saga transitions are unchanged, so growing the enum never silently reopens a
 * terminal state or breaks the choreography path.
 *
 * @author vamuhong
 */
@DisplayName("OrderStatus.canTransitionTo — state machine (unit)")
class OrderStatusTest {

    @Nested
    @DisplayName("Fase 5 fulfillment transitions")
    class FulfillmentTransitions {

        @Test
        @DisplayName("CONFIRMED advances only to SHIPPED or REFUNDED")
        void confirmedAdvancesToShippedOrRefunded() {
            assertThat(CONFIRMED.canTransitionTo(SHIPPED)).isTrue();
            assertThat(CONFIRMED.canTransitionTo(REFUNDED)).isTrue();
            assertThat(CONFIRMED.canTransitionTo(DELIVERED)).isFalse();
            assertThat(CONFIRMED.canTransitionTo(CANCELLED)).isFalse();
            assertThat(CONFIRMED.canTransitionTo(REQUESTED)).isFalse();
        }

        @Test
        @DisplayName("SHIPPED advances only to DELIVERED or REFUNDED")
        void shippedAdvancesToDeliveredOrRefunded() {
            assertThat(SHIPPED.canTransitionTo(DELIVERED)).isTrue();
            assertThat(SHIPPED.canTransitionTo(REFUNDED)).isTrue();
            assertThat(SHIPPED.canTransitionTo(CONFIRMED)).isFalse();
            assertThat(SHIPPED.canTransitionTo(CANCELLED)).isFalse();
        }

        @Test
        @DisplayName("DELIVERED advances only to REFUNDED")
        void deliveredAdvancesToRefundedOnly() {
            assertThat(DELIVERED.canTransitionTo(REFUNDED)).isTrue();
            assertThat(DELIVERED.canTransitionTo(SHIPPED)).isFalse();
            assertThat(DELIVERED.canTransitionTo(CONFIRMED)).isFalse();
            assertThat(DELIVERED.canTransitionTo(CANCELLED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Terminal states")
    class TerminalStates {

        @ParameterizedTest
        @EnumSource(OrderStatus.class)
        @DisplayName("CANCELLED accepts no transition")
        void cancelledIsTerminal(OrderStatus target) {
            assertThat(CANCELLED.canTransitionTo(target)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(OrderStatus.class)
        @DisplayName("REFUNDED accepts no transition")
        void refundedIsTerminal(OrderStatus target) {
            assertThat(REFUNDED.canTransitionTo(target)).isFalse();
        }
    }

    @Nested
    @DisplayName("Existing saga transitions unchanged (regression)")
    class SagaTransitionsUnchanged {

        @Test
        @DisplayName("REQUESTED still reaches the reservation/confirmation/cancel/timeout set")
        void requestedTransitionsUnchanged() {
            assertThat(allowedTargetsOf(REQUESTED)).isEqualTo(EnumSet.of(
                    INVENTORY_RESERVED, INVENTORY_INSUFFICIENT, PENDING_PAYMENT,
                    CONFIRMED, CANCELLED, TIMEOUT));
        }

        @Test
        @DisplayName("INVENTORY_RESERVED still reaches the payment/confirm/cancel/timeout set")
        void inventoryReservedTransitionsUnchanged() {
            assertThat(allowedTargetsOf(INVENTORY_RESERVED)).isEqualTo(EnumSet.of(
                    PAYMENT_AUTHORIZED, PAYMENT_FAILED, PENDING_PAYMENT,
                    CONFIRMED, CANCELLED, TIMEOUT));
        }

        @Test
        @DisplayName("PAYMENT_AUTHORIZED still reaches only CONFIRMED/CANCELLED/TIMEOUT")
        void paymentAuthorizedTransitionsUnchanged() {
            assertThat(allowedTargetsOf(PAYMENT_AUTHORIZED))
                    .isEqualTo(EnumSet.of(CONFIRMED, CANCELLED, TIMEOUT));
        }

        @Test
        @DisplayName("PENDING_PAYMENT still reaches the payment/confirm/cancel/timeout set")
        void pendingPaymentTransitionsUnchanged() {
            assertThat(allowedTargetsOf(PENDING_PAYMENT)).isEqualTo(EnumSet.of(
                    PAYMENT_AUTHORIZED, PAYMENT_FAILED, CONFIRMED, CANCELLED, TIMEOUT));
        }

        @Test
        @DisplayName("Failure/timeout states still collapse only to CANCELLED")
        void failureStatesTransitionToCancelledOnly() {
            assertThat(allowedTargetsOf(PAYMENT_FAILED)).isEqualTo(EnumSet.of(CANCELLED));
            assertThat(allowedTargetsOf(INVENTORY_INSUFFICIENT)).isEqualTo(EnumSet.of(CANCELLED));
            assertThat(allowedTargetsOf(TIMEOUT)).isEqualTo(EnumSet.of(CANCELLED));
        }

        @Test
        @DisplayName("No state may transition to itself")
        void noSelfTransition() {
            for (OrderStatus s : OrderStatus.values()) {
                assertThat(s.canTransitionTo(s))
                        .as("self-transition for %s", s)
                        .isFalse();
            }
        }
    }

    private static Set<OrderStatus> allowedTargetsOf(OrderStatus from) {
        EnumSet<OrderStatus> allowed = EnumSet.noneOf(OrderStatus.class);
        for (OrderStatus target : OrderStatus.values()) {
            if (from.canTransitionTo(target)) {
                allowed.add(target);
            }
        }
        return allowed;
    }
}
