import { Container, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { paymentsApi } from '@api/payments.api';
import { QUERY_KEYS } from '@utils/constants';
import DataTable from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import { formatCurrency, formatDateTime } from '@utils/format';
import type { PaymentResponse } from '@api/types';

export default function PaymentsPage() {
  const { data: payments, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.PAYMENTS],
    queryFn: paymentsApi.getAll,
    staleTime: 5 * 60 * 1000,
  });

  const COLUMNS = [
    { key: 'id', label: 'ID', render: (r: PaymentResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'text.secondary' }}>#{r.id}</Typography> },
    { key: 'orderReference', label: 'Order', render: (r: PaymentResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'primary.main' }}>{r.orderReference}</Typography> },
    { key: 'amount', label: 'Amount', align: 'right' as const, render: (r: PaymentResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{formatCurrency(r.amount)}</Typography> },
    { key: 'paymentMethod', label: 'Method' },
    { key: 'createdDate', label: 'Date', render: (r: PaymentResponse) => <Typography variant="caption" color="text.secondary">{formatDateTime(r.createdDate)}</Typography> },
  ];

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 4 }}>Payments</Typography>
        {isLoading ? (
          <TableSkeleton rows={8} cols={5} />
        ) : (
          <DataTable columns={COLUMNS} rows={payments ?? []} />
        )}
      </motion.div>
    </Container>
  );
}
