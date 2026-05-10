package code.with.vanilson.gatewayservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * GatewayGlobalExceptionHandler
 * <p>
 * Handles all exceptions thrown by gateway filters in a reactive (WebFlux) context.
 * Writes a structured JSON error response for all GatewayBaseException subtypes.
 * All messages are resolved from messages.properties via MessageSource.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Order(-1)
@Component
public class GatewayGlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final MessageSource messageSource;

    public GatewayGlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;
        String errorCode;
        String retryAfter = null;

        if (ex instanceof JwtAuthenticationException jwtEx) {
            status = jwtEx.getHttpStatus();
            message = jwtEx.getMessage();
            errorCode = jwtEx.getMessageKey();
            log.warn("[GatewayExceptionHandler] JWT error: key=[{}] message=[{}] path=[{}]",
                    jwtEx.getMessageKey(), message,
                    exchange.getRequest().getPath().value());

        } else if (ex instanceof RateLimitExceededException rlEx) {
            status = rlEx.getHttpStatus();
            message = rlEx.getMessage();
            errorCode = rlEx.getMessageKey();
            retryAfter = String.valueOf(rlEx.getRetryAfterSeconds());
            log.warn("[GatewayExceptionHandler] Rate limit exceeded: key=[{}] path=[{}]",
                    rlEx.getMessageKey(),
                    exchange.getRequest().getPath().value());

        } else if (ex instanceof LoadSheddingException lsEx) {
            status = lsEx.getHttpStatus();
            message = lsEx.getMessage();
            errorCode = lsEx.getMessageKey();
            retryAfter = "30";
            log.warn("[GatewayExceptionHandler] Load shedding triggered: path=[{}]",
                    exchange.getRequest().getPath().value());

        } else if (ex instanceof ServiceUnavailableException suEx) {
            status = suEx.getHttpStatus();
            message = suEx.getMessage();
            errorCode = suEx.getMessageKey();
            log.error("[GatewayExceptionHandler] Service unavailable: key=[{}] message=[{}]",
                    suEx.getMessageKey(), message);

        } else if (ex instanceof ResponseStatusException rsEx) {
            status = (HttpStatus) rsEx.getStatusCode();
            message = rsEx.getReason();
            if (message == null) {
                message = status.getReasonPhrase();
            }
            errorCode = "gateway.error." + status.value();
            if (status.is4xxClientError()) {
                log.warn("[GatewayExceptionHandler] Client error [{}]: path=[{}] message=[{}]",
                        status.value(), exchange.getRequest().getPath().value(), message);
            } else {
                log.error("[GatewayExceptionHandler] Server error [{}]: path=[{}] message=[{}]",
                        status.value(), exchange.getRequest().getPath().value(), message);
            }

        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            String ref = UUID.randomUUID().toString();

            if (ex.getMessage() != null && ex.getMessage().contains("test")) {
                log.error("[GatewayExceptionHandler] Unhandled exception ref=[{}]: {}",
                        ref, ex.getMessage());
            } else {
                log.error("[GatewayExceptionHandler] Unhandled exception ref=[{}]: {}",
                        ref, ex.getMessage(), ex);
            }

            // User-facing message WITHOUT the reference
            message = messageSource.getMessage(
                    "gateway.error.internal.user",
                    null,
                    LocaleContextHolder.getLocale());
            errorCode = "gateway.error.internal";
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        if (retryAfter != null) {
            exchange.getResponse().getHeaders().set("Retry-After", retryAfter);
        }

        String body = buildErrorBody(status, message, errorCode,
                exchange.getRequest().getPath().value());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String buildErrorBody(HttpStatus status, String message,
                                   String errorCode, String path) {
        return String.format("""
                {
                  "timestamp": "%s",
                  "status": %d,
                  "error": "%s",
                  "errorCode": "%s",
                  "message": "%s",
                  "path": "%s"
                }""",
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode,
                message.replace("\"", "'"),
                path);
    }
}
