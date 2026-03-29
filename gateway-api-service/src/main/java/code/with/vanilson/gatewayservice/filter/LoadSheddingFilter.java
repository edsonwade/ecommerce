package code.with.vanilson.gatewayservice.filter;

import code.with.vanilson.gatewayservice.exception.LoadSheddingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LoadSheddingFilter
 * <p>
 * Global filter that rejects requests when active concurrent requests exceed the configured limit.
 * This is the last line of defense before the system collapses under extreme load.
 * <p>
 * Black Friday scenario:
 * - 10x traffic spike arrives
 * - Active requests reach maxConcurrentRequests
 * - Non-critical requests receive 503 with Retry-After: 30
 * - Core checkout/payment paths are protected
 * <p>
 * Runs AFTER JWT auth (order = HIGHEST_PRECEDENCE + 20).
 * All messages resolved from messages.properties.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Component
public class LoadSheddingFilter implements GlobalFilter, Ordered {

    private final MessageSource messageSource;
    private final int maxConcurrentRequests;
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public LoadSheddingFilter(
            MessageSource messageSource,
            @Value("${gateway.load-shedding.max-concurrent-requests:5000}") int maxConcurrentRequests) {
        this.messageSource = messageSource;
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        int current = activeRequests.incrementAndGet();
        String path = exchange.getRequest().getPath().value();

        if (current > maxConcurrentRequests) {
            activeRequests.decrementAndGet();

            log.warn(messageSource.getMessage(
                    "gateway.log.loadshedding.triggered",
                    new Object[]{current, maxConcurrentRequests, path},
                    LocaleContextHolder.getLocale()));

            String message = messageSource.getMessage(
                    "gateway.loadshedding.rejected",
                    new Object[]{30},
                    LocaleContextHolder.getLocale());

            throw new LoadSheddingException(message, "gateway.loadshedding.rejected");
        }

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    int remaining = activeRequests.decrementAndGet();
                    log.debug("[LoadSheddingFilter] Request completed. Active requests: {}/{}", remaining, maxConcurrentRequests);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
