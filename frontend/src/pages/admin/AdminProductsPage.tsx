import { useState } from 'react';
import {
  Box,
  Button,
  Chip,
  Container,
  Typography,
} from '@mui/material';
import { PauseCircleOutlined, PlayCircleOutlined } from '@mui/icons-material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { productsApi } from '@api/products.api';
import type { ProductResponse, ProductStatus } from '@api/types';
import { normalizeError } from '@api/client';
import DataTable, { type Column } from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import ConfirmDialog from '@components/feedback/ConfirmDialog';
import { useUIStore } from '@stores/ui.store';
import { QUERY_KEYS } from '@utils/constants';

const ADMIN_PRODUCTS_KEY = ['admin-products'] as const;
const PAGE_SIZE = 20;

/**
 * Fase 3 — /admin/products: the full catalogue (every seller, every status,
 * SUSPENDED included — unlike the public catalogue which is ACTIVE-only) with
 * suspend / reactivate actions. Edit/delete already exist on the seller screens
 * and the admin-capable update/delete endpoints; this page owns ONLY the
 * status lifecycle, per the Fase 3 plan.
 */
export default function AdminProductsPage() {
  const qc = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);

  const [page, setPage] = useState(1); // DataTable pagination is 1-based
  const [statusTarget, setStatusTarget] = useState<ProductResponse | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: [...ADMIN_PRODUCTS_KEY, page],
    queryFn: ({ signal }) => productsApi.getAllAdmin(page - 1, PAGE_SIZE, signal),
    staleTime: 30_000,
  });

  const statusMut = useMutation({
    mutationFn: ({ id, status }: { id: number; status: ProductStatus }) =>
      productsApi.setStatus(id, status),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ADMIN_PRODUCTS_KEY });
      // The public catalogue changed too (suspended products vanish from it).
      qc.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCTS] });
      addToast({
        message:
          vars.status === 'SUSPENDED'
            ? 'Product suspended — hidden from the catalogue'
            : 'Product reactivated — visible in the catalogue',
        variant: 'success',
      });
      setStatusTarget(null);
    },
    onError: (err: unknown) => {
      addToast({ message: normalizeError(err).message, variant: 'error' });
      setStatusTarget(null);
    },
  });

  const targetIsSuspended = statusTarget?.status === 'SUSPENDED';

  const COLUMNS: Column<ProductResponse>[] = [
    {
      key: 'id',
      label: 'ID',
      render: (r) => (
        <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'text.secondary' }}>
          {r.id}
        </Typography>
      ),
    },
    {
      key: 'name',
      label: 'Product',
      render: (r) => (
        <Box>
          <Typography variant="body2" sx={{ fontWeight: 500 }}>{r.name}</Typography>
          <Typography variant="caption" color="text.secondary">{r.categoryName}</Typography>
        </Box>
      ),
    },
    {
      key: 'createdBy',
      label: 'Seller',
      render: (r) => (
        <Typography variant="body2" color="text.secondary">
          {r.createdBy ?? '—'}
        </Typography>
      ),
    },
    {
      key: 'price',
      label: 'Price',
      align: 'right',
      render: (r) => (
        <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)' }}>
          ${r.price.toFixed(2)}
        </Typography>
      ),
    },
    {
      key: 'availableQuantity',
      label: 'Stock',
      align: 'right',
      render: (r) => <Typography variant="body2">{r.availableQuantity}</Typography>,
    },
    {
      key: 'status',
      label: 'Status',
      render: (r) => (
        <Chip
          size="small"
          label={r.status === 'SUSPENDED' ? 'Suspended' : 'Active'}
          color={r.status === 'SUSPENDED' ? 'error' : 'success'}
          variant="outlined"
        />
      ),
    },
    {
      key: 'actions',
      label: 'Lifecycle',
      render: (r) =>
        r.status === 'SUSPENDED' ? (
          <Button
            size="small"
            color="success"
            startIcon={<PlayCircleOutlined />}
            onClick={() => setStatusTarget(r)}
          >
            Reactivate
          </Button>
        ) : (
          <Button
            size="small"
            color="error"
            startIcon={<PauseCircleOutlined />}
            onClick={() => setStatusTarget(r)}
          >
            Suspend
          </Button>
        ),
    },
  ];

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 1 }}>Products</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Full catalogue across every seller — suspended products stay listed here even
          though they are hidden from the public store.
        </Typography>

        {isLoading ? (
          <TableSkeleton rows={8} cols={7} />
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
          open={!!statusTarget}
          title={targetIsSuspended ? 'Reactivate product?' : 'Suspend product?'}
          description={
            statusTarget
              ? targetIsSuspended
                ? `"${statusTarget.name}" will become visible and purchasable in the public catalogue again.`
                : `"${statusTarget.name}" will be hidden from the catalogue and search, and any checkout containing it will be cancelled. The seller keeps seeing it in "My products".`
              : undefined
          }
          confirmLabel={targetIsSuspended ? 'Reactivate' : 'Suspend'}
          destructive={!targetIsSuspended}
          loading={statusMut.isPending}
          onConfirm={() =>
            statusTarget &&
            statusMut.mutate({
              id: statusTarget.id,
              status: targetIsSuspended ? 'ACTIVE' : 'SUSPENDED',
            })
          }
          onCancel={() => setStatusTarget(null)}
        />
      </motion.div>
    </Container>
  );
}
