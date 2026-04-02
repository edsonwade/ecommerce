package code.with.vanilson.tenantcontext.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * MissingTenantException — thrown when a request arrives without a valid
 * {@code X-Tenant-ID} header and the endpoint requires tenant context.
 * <p>
 * Maps to HTTP 400 Bad Request — the client must supply a tenant identifier.
 * <p>
 * This is a custom exception — never use generic Spring/Java exceptions.
 *
 * @author vamuhong
 * @version 4.0
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MissingTenantException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "Missing or blank X-Tenant-ID header. Every API request must include a valid tenant identifier.";

    public MissingTenantException() {
        super(DEFAULT_MESSAGE);
    }

    public MissingTenantException(String message) {
        super(message);
    }
}
