import { useTranslation } from 'react-i18next';
import { Box, Button, Chip, CircularProgress, IconButton, Tooltip, Typography } from '@mui/material';
import { DeleteOutlined } from '@mui/icons-material';
import type { ReviewResponse } from '@api/types';
import { useAuthStore } from '@stores/auth.store';
import { formatDate } from '@utils/format';
import StarRating from './StarRating';

interface ReviewListProps {
  reviews: ReviewResponse[];
  loading?: boolean;
  /** True while more pages exist; renders the "show more" control. */
  hasMore?: boolean;
  onLoadMore?: () => void;
  onDelete?: (reviewId: number) => void;
  deletingId?: number | null;
}

/**
 * The published reviews of one product.
 *
 * Authors are never named: the API returns no name (only `customerId`), and showing it would leak
 * an internal identifier. Every review therefore reads "verified buyer" — which is also accurate,
 * since the backend only accepts reviews from confirmed purchasers. The caller's own review is
 * marked so they can find it, and the delete control appears for the owner or an ADMIN, mirroring
 * exactly who the backend's DELETE accepts.
 */
export default function ReviewList({
  reviews,
  loading,
  hasMore,
  onLoadMore,
  onDelete,
  deletingId,
}: ReviewListProps) {
  const { t } = useTranslation();
  const { userId, role } = useAuthStore();
  const isAdmin = role === 'ADMIN';

  if (loading && reviews.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (reviews.length === 0) {
    return (
      <Typography variant="body2" sx={{ color: 'text.secondary', py: 3 }}>
        {t('reviews.none')}
      </Typography>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
      {reviews.map((review) => {
        // userId is a string in the auth store; customerId is numeric on the wire.
        const isOwn = userId != null && String(review.customerId) === String(userId);
        const canDelete = Boolean(onDelete) && (isOwn || isAdmin);

        return (
          <Box
            key={review.id}
            sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.75,
              pb: 2.5,
              borderBottom: '1px solid',
              borderColor: 'var(--border-default)',
              '&:last-of-type': { borderBottom: 'none', pb: 0 },
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
              <StarRating value={review.rating} />
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {t('reviews.verifiedBuyer')} · {formatDate(review.createdAt)}
              </Typography>
              {isOwn && (
                <Chip
                  label={t('reviews.yours')}
                  size="small"
                  sx={{ height: 20, fontSize: '0.65rem' }}
                />
              )}
              {canDelete && (
                <Tooltip title={t('reviews.delete')}>
                  <span style={{ marginLeft: 'auto' }}>
                    <IconButton
                      size="small"
                      aria-label={t('reviews.delete')}
                      disabled={deletingId === review.id}
                      onClick={() => onDelete?.(review.id)}
                    >
                      <DeleteOutlined fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              )}
            </Box>

            {review.comment && (
              <Typography variant="body2" sx={{ color: 'text.primary', whiteSpace: 'pre-wrap' }}>
                {review.comment}
              </Typography>
            )}
          </Box>
        );
      })}

      {hasMore && (
        <Button variant="text" size="small" onClick={onLoadMore} disabled={loading}>
          {t('reviews.loadMore')}
        </Button>
      )}
    </Box>
  );
}
