package code.with.vanilson.customerservice.exception;

import java.util.Map;

public record ErrorResponse(
        Map<String, String> errors
) {
}
