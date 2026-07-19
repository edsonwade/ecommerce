package code.with.vanilson.productservice.review;

/**
 * ReviewEligibilityResponse — outbound DTO telling the caller whether they may review a product (F7, 7.4a).
 * <p>
 * Exists so the storefront can decide whether to render the "write a review" form <em>before</em> the
 * user types anything, instead of letting them write a review only to have the POST rejected.
 * <p>
 * <strong>Deliberately NOT fail-closed, unlike the POST.</strong> When order-service cannot be reached
 * this endpoint answers {@code 200} with {@link Reason#VERIFICATION_UNAVAILABLE} rather than a 503:
 * it is consulted while rendering the product page, and a hard failure here would take the whole page
 * down over a form that is merely hidden. The authority on whether a review is accepted remains
 * {@link ReviewService#createReview}, which stays fail-closed (503) — this response can only ever
 * hide the form, never grant the right to write.
 *
 * @param canReview     true only when the caller has a verified purchase and no existing review
 * @param reason        why {@code canReview} has the value it has (drives the UI's explanatory text)
 * @param existingReview the caller's current review when {@link Reason#ALREADY_REVIEWED}, else null
 *
 * @author vamuhong
 * @version 1.0
 */
public record ReviewEligibilityResponse(
        boolean canReview,
        Reason reason,
        ReviewResponse existingReview) {

    /** Why the caller may (or may not) write a review. Mirrors the POST's outcomes. */
    public enum Reason {
        /** Verified purchase, no review yet — the form may be shown. */
        ELIGIBLE,
        /** The caller already reviewed this product (the POST would 409). */
        ALREADY_REVIEWED,
        /** Verified: the caller never bought this product (the POST would 403). */
        NOT_PURCHASED,
        /** Purchase could not be verified right now (the POST would 503). Form stays hidden. */
        VERIFICATION_UNAVAILABLE
    }

    public static ReviewEligibilityResponse eligible() {
        return new ReviewEligibilityResponse(true, Reason.ELIGIBLE, null);
    }

    public static ReviewEligibilityResponse alreadyReviewed(ReviewResponse existing) {
        return new ReviewEligibilityResponse(false, Reason.ALREADY_REVIEWED, existing);
    }

    public static ReviewEligibilityResponse notPurchased() {
        return new ReviewEligibilityResponse(false, Reason.NOT_PURCHASED, null);
    }

    public static ReviewEligibilityResponse verificationUnavailable() {
        return new ReviewEligibilityResponse(false, Reason.VERIFICATION_UNAVAILABLE, null);
    }
}
