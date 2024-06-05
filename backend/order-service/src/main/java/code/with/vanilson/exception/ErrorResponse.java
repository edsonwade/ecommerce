package code.with.vanilson.exception;

import java.util.Map;

public record ErrorResponse(
        Map<String, String> errors
) {
}
