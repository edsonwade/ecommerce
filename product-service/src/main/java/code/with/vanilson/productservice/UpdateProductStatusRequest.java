package code.with.vanilson.productservice;

import jakarta.validation.constraints.NotNull;

/**
 * UpdateProductStatusRequest — Fase 3 Task 3.4: body of
 * {@code PATCH /api/v1/products/{id}/status} (ADMIN only).
 * <p>
 * {@code null} → 400 via bean validation; an unknown enum literal (e.g. "PAUSED") fails
 * Jackson binding before validation and surfaces as 400 through the existing
 * message-not-readable handling — same convention Fase 2 used for seller-status updates.
 *
 * @param status the new lifecycle status (ACTIVE | SUSPENDED)
 * @author vamuhong
 */
public record UpdateProductStatusRequest(
        @NotNull ProductStatus status) {
}
