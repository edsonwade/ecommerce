import { useState } from 'react';
import { Box, Button, Container, Typography } from '@mui/material';
import { LocalShipping, TaskAlt } from '@mui/icons-material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { ordersApi } from '@api/orders.api';
import { normalizeError } from '@api/client';
import DataTable, { type Column } from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import ConfirmDialog from '@components/feedback/ConfirmDialog';
import OrderStatusBadge from '@components/order/OrderStatusBadge';
import { useUIStore } from '@stores/ui.store';
import { QUERY_KEYS } from '@utils/constants';
import { formatCurrency } from '@utils/format';
import type { FulfillmentStatus, OrderResponse } from '@api/types';

/**
 * Fase 5 — /admin/orders: every order across the platform (GET /orders is ADMIN-only).
 * An admin may advance fulfillment on ANY order, unlike a seller who must own a line.
 * Only the single legal next step is offered: CONFIRMED → SHIPPED → DELIVERED.
 */
function nextStep(status: string): { label: string; to: FulfillmentStatus } | null {
  if (status === 'CONFIRMED') return { label: 'Mark shipped', to: 'SHIPPED' };
  if (status === 'SHIPPED') return { label: 'Mark delivered', to: 'DELIVERED' };
  return null;
}

export default function AdminOrdersPage() {
  const qc = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);

  const [target, setTarget] = useState<{ order: OrderResponse; to: FulfillmentStatus } | null>(null);

  const { data: orders, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.ADMIN_ORDERS],
    queryFn: ({ signal }) => ordersApi.getAll(signal),
    staleTime: 30_000,
  });

  const statusMut = useMutation({
    mutationFn: ({ id, status }: { id: number; status: FulfillmentStatus }) =>
      ordersApi.updateStatus(id, status),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: [QUERY_KEYS.ADMIN_ORDERS] });
      qc.invalidateQueries({ queryKey: [QUERY_KEYS.SELLER_ORDERS] });
      qc.invalidateQueries({ queryKey: [QUERY_KEYS.ORDERS] });
      addToast({
        message: vars.status === 'SHIPPED' ? 'Order marked as shipped' : 'Order marked as delivered',
        variant: 'success',
      });
      setTarget(null);
    },
    onError: (err: unknown) => {
      addToast({ message: normalizeError(err).message, variant: 'error' });
      setTarget(null);
    },
  });

  const COLUMNS: Column<OrderResponse>[] = [
    {
      key: 'reference',
      label: 'Reference',
      render: (r) => (
        <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'primary.main' }}>
          {r.reference}
        </Typography>
      ),
    },
    {
      key: 'customerId',
      label: 'Customer',
      render: (r) => {
        const name = [r.customerFirstname, r.customerLastname].filter(Boolean).join(' ');
        return (
          <Box>
            {name && <Typography variant="body2">{name}</Typography>}
            <Typography variant="caption" sx={{ fontFamily: 'var(--font-mono)', color: 'text.secondary' }}>
              {r.customerId}
            </Typography>
          </Box>
        );
      },
    },
    {
      key: 'amount',
      label: 'Amount',
      align: 'right',
      render: (r) => (
        <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem' }}>
          {formatCurrency(r.amount)}
        </Typography>
      ),
    },
    { key: 'paymentMethod', label: 'Payment' },
    {
      key: 'status',
      label: 'Status',
      render: (r) => <OrderStatusBadge status={r.status} />,
    },
    {
      key: 'fulfillment',
      label: 'Fulfillment',
      render: (r) => {
        const step = nextStep(r.status);
        if (!step) return <Typography variant="caption" color="text.secondary">—</Typography>;
        return (
          <Button
            size="small"
            startIcon={step.to === 'SHIPPED' ? <LocalShipping /> : <TaskAlt />}
            onClick={() => setTarget({ order: r, to: step.to })}
          >
            {step.label}
          </Button>
        );
      },
    },
  ];

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 1 }}>Orders</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Every order across the platform. Fulfillment can be advanced on any order —
          sellers can only advance orders containing their own products.
        </Typography>

        {isLoading ? (
          <TableSkeleton rows={8} cols={6} />
        ) : (
          <DataTable columns={COLUMNS} rows={orders ?? []} />
        )}

        <ConfirmDialog
          open={!!target}
          title={target?.to === 'SHIPPED' ? 'Mark order as shipped?' : 'Mark order as delivered?'}
          description={
            target
              ? target.to === 'SHIPPED'
                ? `Order ${target.order.reference} will be marked as shipped and the customer will see the update.`
                : `Order ${target.order.reference} will be marked as delivered. This is the final fulfillment step.`
              : undefined
          }
          confirmLabel={target?.to === 'SHIPPED' ? 'Mark shipped' : 'Mark delivered'}
          loading={statusMut.isPending}
          onConfirm={() => target && statusMut.mutate({ id: target.order.id, status: target.to })}
          onCancel={() => setTarget(null)}
        />
      </motion.div>
    </Container>
  );
}
