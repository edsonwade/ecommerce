package code.with.vanilson.productservice.review;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ReviewController — Presentation Layer (F7).
 * <p>
 * Nested under {@code /api/v1/products} on purpose: that prefix is already routed by the gateway and
 * already carries the right security rules (GET {@code /products/**} is public; the POST/DELETE below
 * fall through to {@code authenticated()}), so reviews need no new gateway route and no security-config
 * change. The DELETE lives at {@code /products/reviews/{reviewId}} (not a top-level {@code /reviews})
 * for the same routing reason.
 *
 * @author vamuhong
 * @version 1.0
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product Reviews API", description = "Customer product reviews (verified purchase required)")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Write a review for a product (verified buyers only)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Review created"),
            @ApiResponse(responseCode = "403", description = "Caller has not purchased the product"),
            @ApiResponse(responseCode = "404", description = "Product not found or suspended"),
            @ApiResponse(responseCode = "409", description = "Caller already reviewed this product"),
            @ApiResponse(responseCode = "503", description = "Purchase verification unavailable")
    })
    @PostMapping("/{productId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable int productId,
            @RequestBody @Valid ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReview(productId, request));
    }

    @Operation(summary = "List a product's reviews (paginated, public)",
            description = "Use ?page=0&size=10&sort=createdAt,desc. Star average/count are NOT here — "
                    + "they come denormalised on the product response.")
    @ApiResponse(responseCode = "200", description = "Reviews retrieved")
    @GetMapping("/{productId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> getReviews(
            @PathVariable int productId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            @Parameter(hidden = true) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviews(productId, pageable));
    }

    @Operation(summary = "May the caller review this product? (drives whether the form is shown)",
            description = "Answers 200 even when purchase verification is down (reason="
                    + "VERIFICATION_UNAVAILABLE) so the product page still renders; the POST remains "
                    + "the fail-closed authority.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eligibility resolved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Product not found or suspended")
    })
    @GetMapping("/{productId}/reviews/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewEligibilityResponse> getMyEligibility(@PathVariable int productId) {
        return ResponseEntity.ok(reviewService.getEligibility(productId));
    }

    @Operation(summary = "List every review in the tenant (ADMIN moderation)",
            description = "Cross-product moderation feed. Use ?page=0&size=20&sort=createdAt,desc.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reviews retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    })
    @GetMapping("/reviews/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminReviewResponse>> getAllReviewsForModeration(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            @Parameter(hidden = true) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getAllForModeration(pageable));
    }

    @Operation(summary = "Delete a review (ADMIN moderation or the review's own author)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Review deleted"),
            @ApiResponse(responseCode = "403", description = "Not the owner and not an ADMIN"),
            @ApiResponse(responseCode = "404", description = "Review not found")
    })
    @DeleteMapping("/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReview(@PathVariable long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}
