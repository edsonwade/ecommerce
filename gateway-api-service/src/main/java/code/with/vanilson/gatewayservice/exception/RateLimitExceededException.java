package code.with.vanilson.gatewayservice.exception;

import org.springframework.http.HttpStatus;

/**
 * RateLimitExceededException
 * <p>
 * Thrown when a tenant or user exceeds their rate limit quota.
 * HTTP 429 Too Many Requests.
 * Message key: gateway.ratelimit.exceeded
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class RateLimitExceededException extends GatewayBaseException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String resolvedMessage, String messageKey, long retryAfterSeconds) {
        super(resolvedMessage, HttpStatus.TOO_MANY_REQUESTS, messageKey);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
