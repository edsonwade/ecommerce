package code.with.vanilson.orderservice.scheduler;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.OrderStatus;
import code.with.vanilson.orderservice.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SagaTimeoutScheduler — Infrastructure Layer
 * <p>
 * Periodically searches for orders stuck in REQUESTED state beyond
 * the saga.timeout.minutes threshold and marks them as TIMEOUT.
 * This prevents orders from being locked indefinitely if a service fails to respond.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaTimeoutScheduler {

    private final OrderRepository          orderRepository;
    private final MeterRegistry            meterRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${saga.timeout.minutes:15}")
    private int timeoutMinutes;

    /**
     * Periodically check for stuck orders.
     * fixedDelay = 60000ms (1 minute) after completion of the last run.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cancelTimedOutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Order> stuckOrders = orderRepository
                .findByStatusAndCreatedDateBefore(OrderStatus.REQUESTED, cutoff);

        for (Order order : stuckOrders) {
            log.warn("[SagaTimeout] Order timed out: orderId=[{}] correlationId=[{}] createdAt=[{}]",
                    order.getOrderId(), order.getCorrelationId(), order.getCreatedDate());
            order.setStatus(OrderStatus.TIMEOUT);
            orderRepository.save(order);
            meterRegistry.counter("saga.timeout.count").increment();
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    order.getCorrelationId(), OrderStatus.TIMEOUT.name(),
                    order.getReference(), Instant.now()));
        }

        if (!stuckOrders.isEmpty()) {
            log.warn("[SagaTimeout] Cancelled {} timed-out orders", stuckOrders.size());
        }
    }
}
