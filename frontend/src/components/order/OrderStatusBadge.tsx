import { Chip } from '@mui/material';
import type { OrderStatus } from '@api/types';

const STATUS_CONFIG: Record<OrderStatus, { label: string; color: string }> = {
  REQUESTED: { label: 'Requested', color: 'var(--status-info)' },
  INVENTORY_RESERVED: { label: 'Inventory Reserved', color: 'var(--status-warning)' },
  CONFIRMED: { label: 'Confirmed', color: 'var(--status-success)' },
  CANCELLED: { label: 'Cancelled', color: 'var(--status-error)' },
};

export default function OrderStatusBadge({ status }: { status: OrderStatus | string }) {
  const config = STATUS_CONFIG[status as OrderStatus] ?? { label: status, color: 'var(--text-tertiary)' };

  return (
    <Chip
      label={config.label}
      size="small"
      sx={{
        bgcolor: config.color,
        color: '#fff',
        fontFamily: 'var(--font-mono)',
        fontSize: '0.7rem',
        height: 22,
        fontWeight: 500,
      }}
    />
  );
}
