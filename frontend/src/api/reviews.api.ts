import apiClient from './client';
import type {
  AdminReviewResponse,
  PageResponse,
  ReviewEligibility,
  ReviewRequest,
  ReviewResponse,
} from './types';

/**
 * F7 reviews API.
 *
 * Every path sits under `/products` — including the ones that are conceptually about a review rather
 * than a product (`/products/reviews/{id}`, `/products/reviews/admin`). That is deliberate on the
 * backend: the `/api/v1/products` prefix is already routed by the gateway, so reviews needed no new
 * gateway route. Do NOT "tidy" these into `/reviews/...` — a new prefix 404s until it is added in
 * BOTH the config-service predicates and the gateway's GATEWAY_PUBLIC_PATHS env.
 */
export const reviewsApi = {
  /** Public, paginated, newest-first (the backend defaults to sort=createdAt,desc). */
  list: (productId: number, page = 0, size = 10, signal?: AbortSignal) =>
    apiClient
      .get<PageResponse<ReviewResponse>>(`/products/${productId}/reviews`, {
        params: { page, size },
        signal,
      })
      .then((r) => r.data),

  create: (productId: number, data: ReviewRequest) =>
    apiClient.post<ReviewResponse>(`/products/${productId}/reviews`, data).then((r) => r.data),

  remove: (reviewId: number) =>
    apiClient.delete(`/products/reviews/${reviewId}`).then(() => undefined),

  /**
   * Whether the current caller may review this product. Requires auth (401 otherwise), so callers
   * must gate the query on an authenticated buyer rather than letting it fire for guests.
   */
  eligibility: (productId: number, signal?: AbortSignal) =>
    apiClient
      .get<ReviewEligibility>(`/products/${productId}/reviews/me`, { signal })
      .then((r) => r.data),

  /** ADMIN-only cross-product moderation feed. */
  listAdmin: (page = 0, size = 20, signal?: AbortSignal) =>
    apiClient
      .get<PageResponse<AdminReviewResponse>>('/products/reviews/admin', {
        params: { page, size },
        signal,
      })
      .then((r) => r.data),
};
