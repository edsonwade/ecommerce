package code.with.vanilson.gatewayservice.exception;

import org.springframework.http.HttpStatus;

/**
 * LoadSheddingException
 * <p>
 * Thrown when the gateway rejects a request due to active request limit being exceeded.
 * This protects the system under extreme load (Black Friday scenario).
 * HTTP 503 Service Unavailable.
 * Message key: gateway.loadshedding.rejected
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class LoadSheddingException extends GatewayBaseException {

    public LoadSheddingException(String resolvedMessage, String messageKey) {
        super(resolvedMessage, HttpStatus.SERVICE_UNAVAILABLE, messageKey);
    }
}
