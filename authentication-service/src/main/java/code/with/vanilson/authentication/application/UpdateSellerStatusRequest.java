package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.SellerStatus;
import jakarta.validation.constraints.NotNull;

/**
 * UpdateSellerStatusRequest — Application Layer DTO
 * Payload received on PATCH /api/v1/auth/users/{userId}/seller-status (ADMIN only).
 * <p>
 * APPROVED reactivates/approves a seller; SUSPENDED blocks product writes and revokes
 * every session; PENDING_APPROVAL puts the seller back in the review queue.
 * The target user must have role SELLER.
 * </p>
 */
public record UpdateSellerStatusRequest(
        @NotNull(message = "{auth.validation.seller.status.required}")
        SellerStatus status
) {}
