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
        ProductStatus status,
        /*
         * Fase 7 (Task 7.3, Decision A1): denormalised review counters, read straight off the
         * product row at zero query cost. Never null — a product with no reviews is 0.0 / 0.
         * Additive and last, like status, so existing frontend deserialization is unaffected.
         */
        BigDecimal averageRating,
        int reviewCount) {

}
