package code.with.vanilson.gatewayservice.exception;

import org.springframework.http.HttpStatus;

/**
 * ServiceUnavailableException
 * <p>
 * Thrown when a downstream service is unreachable and no circuit-breaker fallback
 * can satisfy the request. HTTP 503.
 * Message key: gateway.error.service.unavailable
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class ServiceUnavailableException extends GatewayBaseException {

    public ServiceUnavailableException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey);
    }

    public ServiceUnavailableException(String resolvedMessage, String messageKey, Throwable cause) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey, cause);
    }
}
