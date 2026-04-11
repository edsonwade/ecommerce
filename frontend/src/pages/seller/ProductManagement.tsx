import { useState } from 'react';
import { Box, Button, Container, Typography } from '@mui/material';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { productsApi } from '@api/products.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useUIStore } from '@stores/ui.store';
import DataTable from '@components/data-display/DataTable';
import ConfirmDialog from '@components/feedback/ConfirmDialog';
import { formatCurrency } from '@utils/format';
import type { ProductResponse } from '@api/types';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';

export default function ProductManagement() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const [page, setPage] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<ProductResponse | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.PRODUCTS, page, 20],
    queryFn: () => productsApi.getAll(page, 20),
    staleTime: 2 * 60 * 1000,
  });

  const { mutate: deleteProduct, isPending: deleting } = useMutation({
    mutationFn: (id: number) => productsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCTS] });
      addToast({ message: 'Product deleted', variant: 'success' });
      setDeleteTarget(null);
    },
    onError: () => {
      addToast({ message: 'Failed to delete product', variant: 'error' });
    },
  });

  const COLUMNS = [
    { key: 'id', label: 'ID', render: (r: ProductResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'text.secondary' }}>#{r.id}</Typography> },
    { key: 'name', label: 'Name', render: (r: ProductResponse) => <Typography variant="body2" sx={{ fontWeight: 500 }}>{r.name}</Typography> },
    { key: 'categoryName', label: 'Category', render: (r: ProductResponse) => <Typography variant="caption" sx={{ color: 'text.secondary', letterSpacing: '0.06em' }}>{r.categoryName?.toUpperCase()}</Typography> },
    { key: 'price', label: 'Price', align: 'right' as const, render: (r: ProductResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main', fontSize: '0.875rem' }}>{formatCurrency(r.price)}</Typography> },
    { key: 'availableQuantity', label: 'Stock', align: 'right' as const, render: (r: ProductResponse) => (
      <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem', color: r.availableQuantity < 5 ? 'var(--status-error)' : 'text.primary' }}>
        {r.availableQuantity}
      </Typography>
    )},
  ];

  return (
    <Container maxWidth="lg" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4, flexWrap: 'wrap', gap: 2 }}>
          <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)' }}>Products</Typography>
          <Button component={Link} to={ROUTES.SELLER_PRODUCT_NEW} variant="contained">
            + Add product
          </Button>
        </Box>

        {isLoading ? (
          <TableSkeleton rows={8} cols={5} />
        ) : (
          <DataTable
            columns={COLUMNS}
            rows={data?.content ?? []}
            totalPages={data?.totalPages}
            page={page + 1}
            onPageChange={(p) => setPage(p - 1)}
            onEdit={(row) => navigate(ROUTES.SELLER_PRODUCT_EDIT(row.id))}
            onDelete={(row) => setDeleteTarget(row)}
          />
        )}

        <ConfirmDialog
          open={!!deleteTarget}
          title="Delete product"
          description={`Are you sure you want to delete "${deleteTarget?.name}"? This action cannot be undone.`}
          confirmLabel="Delete"
          destructive
          loading={deleting}
          onConfirm={() => deleteTarget && deleteProduct(deleteTarget.id)}
          onCancel={() => setDeleteTarget(null)}
        />
      </motion.div>
    </Container>
  );
}
