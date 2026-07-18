package code.with.vanilson.productservice.review;

import java.time.LocalDateTime;

/**
 * ReviewResponse — outbound DTO for a single review (F7).
 * Returned inside a {@code Page<ReviewResponse>} (project pagination convention, C2).
 *
 * @author vamuhong
 * @version 1.0
 */
public record ReviewResponse(
        Long id,
        Integer productId,
        Long customerId,
        int rating,
        String comment,
        LocalDateTime createdAt) {
}
