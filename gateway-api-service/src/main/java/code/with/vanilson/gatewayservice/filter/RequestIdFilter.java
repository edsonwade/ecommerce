package code.with.vanilson.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * RequestIdFilter
 * <p>
 * Global filter that assigns a unique X-Request-ID to every request entering the gateway.
 * If the client already provides an X-Request-ID, it is preserved.
 * This ID is propagated to all downstream services for distributed tracing.
 * <p>
 * Runs first (HIGHEST_PRECEDENCE) so all other filters have access to the request ID.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final MessageSource messageSource;

    public RequestIdFilter(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existingRequestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        String requestId = (existingRequestId != null && !existingRequestId.isBlank())
                ? existingRequestId
                : UUID.randomUUID().toString();

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");

        log.info(messageSource.getMessage(
                "gateway.log.request.received",
                new Object[]{method, path, tenantId != null ? tenantId : "unknown", requestId},
                LocaleContextHolder.getLocale()));

        // Propagate request ID to downstream service
        var mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        // Also expose it in the response for clients to correlate
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
