package code.with.vanilson.productservice;

import java.math.BigDecimal;

public record ProductResponse(
        Integer id,
        String name,
        String description,
        double availableQuantity,
        BigDecimal price,
        Integer categoryId,
        String categoryName,
        String categoryDescription,
        String createdBy,
        String imageUrl,
        /* Fase 3: lifecycle status (ACTIVE | SUSPENDED). Additive — old clients ignore it. */
        ProductStatus status) {

}
