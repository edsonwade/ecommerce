package code.with.vanilson.productservice.review;

import java.time.LocalDateTime;

/**
 * AdminReviewResponse — outbound DTO for the cross-product moderation list (F7, 7.4a).
 * <p>
 * Same shape as {@link ReviewResponse} plus {@code productName}: the moderation table lists reviews
 * from every product at once, so a bare {@code productId} would be unreadable. The name is joined in
 * the query (see {@link ReviewRepository#findAllForModeration}) rather than fetched per row, so the
 * page costs one statement regardless of page size.
 * <p>
 * Customer identity is deliberately limited to {@code customerId} — the storefront shows no names
 * (reviews render as "verified buyer"), and moderation needs only to tell authors apart.
 *
 * @author vamuhong
 * @version 1.0
 */
public record AdminReviewResponse(
        Long id,
        Integer productId,
        String productName,
        Long customerId,
        int rating,
        String comment,
        LocalDateTime createdAt) {
}
