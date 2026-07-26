import { useState } from 'react';
import { Box, Container, IconButton, Tooltip, Typography } from '@mui/material';
import { DeleteOutlined } from '@mui/icons-material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { reviewsApi } from '@api/reviews.api';
import type { AdminReviewResponse } from '@api/types';
import { normalizeError } from '@api/client';
import DataTable, { type Column } from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import ConfirmDialog from '@components/feedback/ConfirmDialog';
import StarRating from '@components/product/StarRating';
import { useUIStore } from '@stores/ui.store';
import { QUERY_KEYS } from '@utils/constants';
import { formatDate, truncate } from '@utils/format';

const PAGE_SIZE = 20;

/**
 * F7 Task 7.4b — /admin/reviews: every review in the tenant, newest-first, with delete.
 *
 * Moderation is ADMIN-only by design: sellers cannot delete reviews of their own products, which
 * is the whole point of the backend restricting DELETE to the author or an ADMIN. This page is the
 * cross-product view; the same delete is also reachable inline on each product page.
 *
 * Deleting recomputes the product's rating counters server-side, so this invalidates the product
 * detail cache — but never the catalogue list, matching Decision A1's no-evict policy.
 */
export default function AdminReviewsPage() {
  const qc = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);

  const [page, setPage] = useState(1); // DataTable pagination is 1-based
  const [deleteTarget, setDeleteTarget] = useState<AdminReviewResponse | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.ADMIN_REVIEWS, page],
    queryFn: ({ signal }) => reviewsApi.listAdmin(page - 1, PAGE_SIZE, signal),
    staleTime: 30_000,
  });

  const deleteMut = useMutation({
    mutationFn: (reviewId: number) => reviewsApi.remove(reviewId),
    onSuccess: (_, reviewId) => {
      const productId = deleteTarget?.productId;
      void qc.invalidateQueries({ queryKey: [QUERY_KEYS.ADMIN_REVIEWS] });
      if (productId != null) {
        void qc.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCT, productId] });
        void qc.invalidateQueries({ queryKey: [QUERY_KEYS.REVIEWS, productId] });
      }
      addToast({ message: `Review #${reviewId} deleted`, variant: 'success' });
      setDeleteTarget(null);
    },
    onError: (err: unknown) => {
      addToast({ message: normalizeError(err).message, variant: 'error' });
      setDeleteTarget(null);
    },
  });

  const COLUMNS: Column<AdminReviewResponse>[] = [
    {
      key: 'productName',
      label: 'Product',
      render: (r) => (
        <Box>
          <Typography variant="body2" sx={{ fontWeight: 500 }}>{r.productName}</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'var(--font-mono)' }}>
            #{r.productId}
          </Typography>
        </Box>
      ),
    },
    {
      key: 'rating',
      label: 'Rating',
      render: (r) => <StarRating value={r.rating} />,
    },
    {
      key: 'comment',
      label: 'Comment',
      render: (r) => (
        <Typography variant="body2" color={r.comment ? 'text.primary' : 'text.secondary'}>
          {r.comment ? truncate(r.comment, 120) : '—'}
        </Typography>
      ),
    },
    {
      key: 'customerId',
      label: 'Customer',
      render: (r) => (
        <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}>
          {r.customerId}
        </Typography>
      ),
    },
    {
      key: 'createdAt',
      label: 'Date',
      render: (r) => (
        <Typography variant="body2" color="text.secondary">{formatDate(r.createdAt)}</Typography>
      ),
    },
    {
      key: 'actions',
      label: '',
      align: 'right',
      render: (r) => (
        <Tooltip title="Delete review">
          <IconButton size="small" color="error" onClick={() => setDeleteTarget(r)}>
            <DeleteOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      ),
    },
  ];

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 1 }}>Reviews</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Every review across the catalogue, newest first. Deleting one recomputes that product’s
          star average immediately.
        </Typography>

        {isLoading ? (
          <TableSkeleton rows={8} cols={6} />
        ) : (
          <DataTable
            columns={COLUMNS}
            rows={data?.content ?? []}
            totalPages={data?.totalPages}
            page={page}
            onPageChange={setPage}
          />
        )}

        <ConfirmDialog
          open={!!deleteTarget}
          title="Delete review?"
          description={
            deleteTarget
              ? `This removes the review of "${deleteTarget.productName}" permanently and recomputes the product's rating. This cannot be undone.`
              : undefined
          }
          confirmLabel="Delete"
          destructive
          loading={deleteMut.isPending}
          onConfirm={() => deleteTarget && deleteMut.mutate(deleteTarget.id)}
          onCancel={() => setDeleteTarget(null)}
        />
      </motion.div>
    </Container>
  );
}
