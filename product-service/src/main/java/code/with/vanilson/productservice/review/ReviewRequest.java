package code.with.vanilson.productservice.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ReviewRequest — inbound DTO for POST a review (F7).
 *
 * @param rating  1–5 stars (required)
 * @param comment optional free text, ≤ 2000 chars
 *
 * @author vamuhong
 * @version 1.0
 */
public record ReviewRequest(
        @NotNull(message = "{review.rating.required}")
        @Min(value = 1, message = "{review.rating.range}")
        @Max(value = 5, message = "{review.rating.range}")
        Integer rating,

        @Size(max = 2000, message = "{review.comment.too.long}")
        String comment) {
}
