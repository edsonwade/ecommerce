import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Typography } from '@mui/material';
import { reviewsApi } from '@api/reviews.api';
import { normalizeError } from '@api/client';
import type { ReviewResponse } from '@api/types';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { QUERY_KEYS } from '@utils/constants';
import ReviewForm from './ReviewForm';
import ReviewList from './ReviewList';
import StarRating from './StarRating';

const PAGE_SIZE = 10;

interface ReviewsSectionProps {
  productId: number;
  averageRating?: number;
  reviewCount?: number;
}

/**
 * The whole reviews block of the product page: summary stars, the eligibility-gated write form,
 * and the paginated list. Mounted by ProductPage below the purchase block — everything here is
 * additive and never touches the add-to-cart/idempotency flow.
 *
 * Cache discipline (Decision A1): after a create or delete we invalidate this product's detail,
 * its reviews, and the caller's eligibility — and deliberately NOT the catalogue list
 * (`QUERY_KEYS.PRODUCTS`). The grid's stars converge when its TTL expires; invalidating the list
 * on every review write is exactly the latency regression A1 exists to prevent.
 */
export default function ReviewsSection({ productId, averageRating, reviewCount }: ReviewsSectionProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const { isAuthenticated, role } = useAuthStore();
  // Only buyers can ever be eligible; don't fire an authenticated-only query for guests,
  // and sellers/admins don't buy (WORKING_STATE roles), so the form can never apply to them.
  const isBuyer = isAuthenticated && role === 'USER';

  const [visiblePages, setVisiblePages] = useState(1);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const reviewsQuery = useQuery({
    // Reviews are fetched newest-first, one growing window instead of true page flipping:
    // "show more" bumps the size so the list stays a single query object.
    queryKey: [QUERY_KEYS.REVIEWS, productId, visiblePages],
    queryFn: ({ signal }) => reviewsApi.list(productId, 0, visiblePages * PAGE_SIZE, signal),
  });

  const eligibilityQuery = useQuery({
    queryKey: [QUERY_KEYS.REVIEW_ELIGIBILITY, productId],
    queryFn: ({ signal }) => reviewsApi.eligibility(productId, signal),
    enabled: isBuyer,
    // VERIFICATION_UNAVAILABLE arrives as a 200, so errors here are unexpected — don't hammer.
    retry: false,
  });

  const invalidateAfterWrite = () => {
    void queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCT, productId] });
    void queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.REVIEWS, productId] });
    void queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.REVIEW_ELIGIBILITY, productId] });
    // NEVER invalidate [QUERY_KEYS.PRODUCTS] here — see the component docstring (Decision A1).
  };

  const createMutation = useMutation({
    mutationFn: (data: { rating: number; comment?: string }) => reviewsApi.create(productId, data),
    onSuccess: () => {
      addToast({ message: t('reviews.created'), variant: 'success' });
      invalidateAfterWrite();
    },
    onError: (err: unknown) => {
      // Map the POST's contract onto explanatory toasts; anything else falls back to the message.
      const error = normalizeError(err);
      const message =
        error.status === 403
          ? t('reviews.notPurchased')
          : error.status === 409
            ? t('reviews.alreadyReviewed')
            : error.status === 503
              ? t('reviews.unavailable')
              : error.message;
      addToast({ message, variant: 'error' });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (reviewId: number) => reviewsApi.remove(reviewId),
    onSuccess: () => {
      addToast({ message: t('reviews.deleted'), variant: 'success' });
      invalidateAfterWrite();
    },
    onError: (err: unknown) => addToast({ message: normalizeError(err).message, variant: 'error' }),
    onSettled: () => setDeletingId(null),
  });

  const handleDelete = (reviewId: number) => {
    if (!window.confirm(t('reviews.deleteConfirm'))) return;
    setDeletingId(reviewId);
    deleteMutation.mutate(reviewId);
  };

  const reviews: ReviewResponse[] = reviewsQuery.data?.content ?? [];
  const eligibility = eligibilityQuery.data;
  const showForm = isBuyer && eligibility?.canReview === true;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 4 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
        <Typography variant="h6" sx={{ fontFamily: 'var(--font-serif)' }}>
          {t('reviews.title')}
        </Typography>
        {typeof averageRating === 'number' && (reviewCount ?? 0) > 0 && (
          <StarRating value={averageRating} count={reviewCount} />
        )}
      </Box>

      {/* Explain a hidden form only when the caller asked to review before (ALREADY_REVIEWED is
          self-evident from the badge in the list, NOT_PURCHASED gets a hint, outage gets a soft note). */}
      {isBuyer && eligibility && !eligibility.canReview && eligibility.reason === 'NOT_PURCHASED' && (
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {t('reviews.notPurchased')}
        </Typography>
      )}
      {isBuyer && eligibility && eligibility.reason === 'VERIFICATION_UNAVAILABLE' && (
        <Alert severity="info" sx={{ py: 0 }}>
          {t('reviews.unavailable')}
        </Alert>
      )}

      {showForm && (
        <ReviewForm
          onSubmit={(data) => createMutation.mutate(data)}
          submitting={createMutation.isPending}
        />
      )}

      <ReviewList
        reviews={reviews}
        loading={reviewsQuery.isFetching}
        hasMore={reviewsQuery.data ? !reviewsQuery.data.last : false}
        onLoadMore={() => setVisiblePages((p) => p + 1)}
        onDelete={isAuthenticated ? handleDelete : undefined}
        deletingId={deletingId}
      />
    </Box>
  );
}
