package code.with.vanilson.productservice.exception;

import java.util.Map;

public record ErrorResponse(
        Map<String, String> errors
) {
}
