import { Container, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ordersApi } from '@api/orders.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import DataTable from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import { formatCurrency } from '@utils/format';
import type { OrderResponse } from '@api/types';

export default function OrderManagement() {
  const navigate = useNavigate();
  const { data: orders, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.ORDERS],
    queryFn: ({ signal }) => ordersApi.getAll(signal),
    staleTime: 30 * 1000,
  });

  const COLUMNS = [
    { key: 'reference', label: 'Reference', render: (r: OrderResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'primary.main' }}>{r.reference}</Typography> },
    { key: 'amount', label: 'Amount', align: 'right' as const, render: (r: OrderResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem' }}>{formatCurrency(r.amount)}</Typography> },
    { key: 'paymentMethod', label: 'Payment' },
    { key: 'customerId', label: 'Customer', render: (r: OrderResponse) => <Typography variant="caption" sx={{ fontFamily: 'var(--font-mono)', color: 'text.secondary' }}>{r.customerId.slice(0, 12)}…</Typography> },
  ];

  return (
    <Container maxWidth="lg" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 4 }}>Orders</Typography>
        {isLoading ? (
          <TableSkeleton rows={8} cols={4} />
        ) : (
          <DataTable
            columns={COLUMNS}
            rows={orders ?? []}
            onView={(row) => navigate(ROUTES.ORDER_DETAIL(row.id))}
          />
        )}
      </motion.div>
    </Container>
  );
}
