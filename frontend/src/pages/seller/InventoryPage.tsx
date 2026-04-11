import { Box, Container, Chip, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { productsApi } from '@api/products.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import DataTable from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import type { ProductResponse } from '@api/types';

function StockBadge({ qty }: { qty: number }) {
  const color = qty === 0 ? 'var(--status-error)' : qty < 5 ? 'var(--status-warning)' : 'var(--status-success)';
  const label = qty === 0 ? 'Out of stock' : qty < 5 ? 'Low stock' : 'In stock';
  return <Chip label={label} size="small" sx={{ bgcolor: color, color: '#fff', fontSize: '0.65rem', height: 20 }} />;
}

export default function InventoryPage() {
  const navigate = useNavigate();
  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.PRODUCTS, 0, 100],
    queryFn: () => productsApi.getAll(0, 100),
    staleTime: 2 * 60 * 1000,
  });

  const COLUMNS = [
    { key: 'id', label: 'ID', render: (r: ProductResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'text.secondary' }}>#{r.id}</Typography> },
    { key: 'name', label: 'Name', render: (r: ProductResponse) => <Typography variant="body2" sx={{ fontWeight: 500 }}>{r.name}</Typography> },
    { key: 'categoryName', label: 'Category', render: (r: ProductResponse) => <Typography variant="caption" color="text.secondary">{r.categoryName}</Typography> },
    {
      key: 'availableQuantity',
      label: 'Stock',
      align: 'right' as const,
      render: (r: ProductResponse) => (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 1.5 }}>
          <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem' }}>{r.availableQuantity}</Typography>
          <StockBadge qty={r.availableQuantity} />
        </Box>
      ),
    },
  ];

  const products = data?.content ?? [];

  return (
    <Container maxWidth="lg" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Box sx={{ mb: 4 }}>
          <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)' }}>Inventory</Typography>
          <Typography variant="body2" color="text.secondary">
            {products.filter((p) => p.availableQuantity < 5).length} items need attention
          </Typography>
        </Box>

        {isLoading ? (
          <TableSkeleton rows={8} cols={4} />
        ) : (
          <DataTable
            columns={COLUMNS}
            rows={products.sort((a, b) => a.availableQuantity - b.availableQuantity)}
            onEdit={(row) => navigate(ROUTES.SELLER_PRODUCT_EDIT(row.id))}
          />
        )}
      </motion.div>
    </Container>
  );
}
