package code.with.vanilson.productservice.category;

import jakarta.validation.constraints.NotBlank;

/**
 * CategoryRequest — Application Layer DTO (Fase 4).
 * <p>
 * Inbound payload for ADMIN category create/update. {@code name} is required and
 * unique (enforced at the service layer + DB {@code unique_name} constraint);
 * {@code description} is optional. Validation messages are inline to match the
 * existing {@code ProductRequest} style.
 */
public record CategoryRequest(

        @NotBlank(message = "Category name is required")
        String name,

        String description
) {
}
