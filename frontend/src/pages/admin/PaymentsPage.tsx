import { useState } from 'react';
import { Button, Chip, Container, Typography } from '@mui/material';
import { Undo } from '@mui/icons-material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { paymentsApi } from '@api/payments.api';
import { normalizeError } from '@api/client';
import { QUERY_KEYS } from '@utils/constants';
import DataTable from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import ConfirmDialog from '@components/feedback/ConfirmDialog';
import { useUIStore } from '@stores/ui.store';
import { formatCurrency, formatDateTime } from '@utils/format';
import type { PaymentResponse } from '@api/types';

export default function PaymentsPage() {
  const qc = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const [refundTarget, setRefundTarget] = useState<PaymentResponse | null>(null);

  const { data: payments, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.PAYMENTS],
    queryFn: paymentsApi.getAll,
    staleTime: 5 * 60 * 1000,
  });

  const refundMut = useMutation({
    mutationFn: (id: number) => paymentsApi.refund(id),
    onSuccess: (updated) => {
      // Optimistically flip the row to REFUNDED from the mutation's own response, so the
      // chip updates immediately and is immune to the background refetch's latency
      // (the refetch below still reconciles). Fixes the "stays Authorized until reload" bug.
      qc.setQueryData<PaymentResponse[]>([QUERY_KEYS.PAYMENTS], (old) =>
        (old ?? []).map((p) => (p.paymentId === updated.paymentId ? { ...p, status: 'REFUNDED' } : p)),
      );
      qc.invalidateQueries({ queryKey: [QUERY_KEYS.PAYMENTS] });
      addToast({ message: 'Payment refunded', variant: 'success' });
      setRefundTarget(null);
    },
    onError: (err: unknown) => {
      addToast({ message: normalizeError(err).message, variant: 'error' });
      setRefundTarget(null);
    },
  });

  // DataTable requires an `id` field on each row; the API's real key is `paymentId`.
  const rows = (payments ?? []).map((p) => ({ ...p, id: p.paymentId }));

  const COLUMNS = [
    { key: 'id', label: 'ID', render: (r: PaymentResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'text.secondary' }}>#{r.paymentId}</Typography> },
    { key: 'orderReference', label: 'Order', render: (r: PaymentResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'primary.main' }}>{r.orderReference}</Typography> },
    { key: 'amount', label: 'Amount', align: 'right' as const, render: (r: PaymentResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{formatCurrency(r.amount)}</Typography> },
    { key: 'paymentMethod', label: 'Method' },
    { key: 'createdDate', label: 'Date', render: (r: PaymentResponse) => <Typography variant="caption" color="text.secondary">{formatDateTime(r.createdDate)}</Typography> },
    {
      key: 'status',
      label: 'Status',
      render: (r: PaymentResponse) => (
        <Chip
          size="small"
          label={r.status === 'REFUNDED' ? 'Refunded' : 'Authorized'}
          color={r.status === 'REFUNDED' ? 'warning' : 'success'}
          variant="outlined"
        />
      ),
    },
    {
      key: 'actions',
      label: 'Refund',
      render: (r: PaymentResponse) =>
        r.status === 'REFUNDED' ? (
          <Typography variant="caption" color="text.secondary">—</Typography>
        ) : (
          <Button size="small" color="warning" startIcon={<Undo />} onClick={() => setRefundTarget(r)}>
            Refund
          </Button>
        ),
    },
  ];

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 4 }}>Payments</Typography>
        {isLoading ? (
          <TableSkeleton rows={8} cols={7} />
        ) : (
          <DataTable columns={COLUMNS} rows={rows} />
        )}

        <ConfirmDialog
          open={!!refundTarget}
          title="Refund this payment?"
          description={
            refundTarget
              ? `Payment #${refundTarget.paymentId} for order ${refundTarget.orderReference} (${formatCurrency(refundTarget.amount)}) will be refunded. This cannot be undone.`
              : undefined
          }
          confirmLabel="Refund"
          destructive
          loading={refundMut.isPending}
          onConfirm={() => refundTarget && refundMut.mutate(refundTarget.paymentId)}
          onCancel={() => setRefundTarget(null)}
        />
      </motion.div>
    </Container>
  );
}
