package code.with.vanilson.gatewayservice.fallback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * FallbackController — Presentation Layer (Gateway)
 * <p>
 * Handles circuit-breaker fallback responses for all downstream services.
 * Returns structured JSON with Retry-After header so clients know when to retry.
 * All messages resolved from messages.properties via MessageSource.
 * <p>
 * Phase 2 addition: /fallback/carts + /fallback/auth endpoints.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/fallback")
public class FallbackController {

    private final MessageSource messageSource;

    @RequestMapping("/orders")
    public Mono<ResponseEntity<Map<String, Object>>> ordersFallback(ServerWebExchange exchange) {
        return buildFallback("gateway.fallback.orders", "order-service", exchange);
    }

    @RequestMapping("/products")
    public Mono<ResponseEntity<Map<String, Object>>> productsFallback(ServerWebExchange exchange) {
        return buildFallback("gateway.fallback.products", "product-service", exchange);
    }

    @RequestMapping("/customers")
    public Mono<ResponseEntity<Map<String, Object>>> customersFallback(ServerWebExchange exchange) {
        return buildFallback("gateway.fallback.customers", "customer-service", exchange);
    }

    @RequestMapping("/payments")
    public Mono<ResponseEntity<Map<String, Object>>> paymentsFallback(ServerWebExchange exchange) {
        return buildFallback("gateway.fallback.payments", "payment-service", exchange);
    }

    @RequestMapping("/carts")
    public Mono<ResponseEntity<Map<String, Object>>> cartsFallback(ServerWebExchange exchange) {
        return buildFallback("gateway.fallback.carts", "cart-service", exchange);
    }

    @RequestMapping("/generic")
    public Mono<ResponseEntity<Map<String, Object>>> genericFallback(ServerWebExchange exchange) {
        return buildFallback("gateway.fallback.generic", "unknown-service", exchange);
    }

    // -------------------------------------------------------

    private Mono<ResponseEntity<Map<String, Object>>> buildFallback(
            String messageKey, String serviceName, ServerWebExchange exchange) {

        String message  = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");

        log.warn(messageSource.getMessage(
                "gateway.log.fallback.triggered",
                new Object[]{exchange.getRequest().getPath().value(), serviceName},
                LocaleContextHolder.getLocale()));

        Map<String, Object> body = Map.of(
                "timestamp",   Instant.now().toString(),
                "status",      HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error",       "Service Unavailable",
                "message",     message,
                "service",     serviceName,
                "requestId",   requestId != null ? requestId : "unknown",
                "retryAfter",  30
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(body));
    }
}
