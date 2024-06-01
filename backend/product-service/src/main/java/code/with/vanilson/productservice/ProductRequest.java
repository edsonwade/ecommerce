package code.with.vanilson.productservice;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(
        @NotNull Integer id,
        @NotNull @NotEmpty String name,
        @NotNull @NotEmpty String description,
        @NotNull Double availableQuantity, // Changed to Double
        @NotNull BigDecimal price
) {
}

