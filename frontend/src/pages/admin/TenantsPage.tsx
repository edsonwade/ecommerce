import { Container, Typography, Chip } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { tenantsApi } from '@api/tenants.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import DataTable from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import type { TenantResponse, TenantStatus } from '@api/types';

const STATUS_COLOR: Record<TenantStatus, string> = {
  ACTIVE: 'var(--status-success)',
  SUSPENDED: 'var(--status-warning)',
  CANCELLED: 'var(--status-error)',
};

export default function TenantsPage() {
  const navigate = useNavigate();
  const { data: tenants, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.TENANTS],
    queryFn: tenantsApi.getAll,
    staleTime: 10 * 60 * 1000,
  });

  const COLUMNS = [
    { key: 'tenantId', label: 'ID', render: (r: TenantResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'text.secondary' }}>{r.tenantId.slice(0, 8)}…</Typography> },
    { key: 'name', label: 'Name', render: (r: TenantResponse) => <Typography variant="body2" sx={{ fontWeight: 500 }}>{r.name}</Typography> },
    { key: 'slug', label: 'Slug', render: (r: TenantResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'primary.main' }}>{r.slug}</Typography> },
    { key: 'plan', label: 'Plan', render: (r: TenantResponse) => <Typography variant="caption" sx={{ letterSpacing: '0.06em' }}>{r.plan}</Typography> },
    { key: 'status', label: 'Status', render: (r: TenantResponse) => <Chip label={r.status} size="small" sx={{ bgcolor: STATUS_COLOR[r.status], color: '#fff', fontSize: '0.65rem', height: 20 }} /> },
    { key: 'contactEmail', label: 'Contact', render: (r: TenantResponse) => <Typography variant="caption" color="text.secondary">{r.contactEmail}</Typography> },
  ];

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 4 }}>Tenants</Typography>
        {isLoading ? (
          <TableSkeleton rows={8} cols={6} />
        ) : (
          <DataTable
            columns={COLUMNS}
            rows={(tenants ?? []).map((t) => ({ ...t, id: t.tenantId }))}
            onView={(row) => navigate(ROUTES.ADMIN_TENANT(row.tenantId))}
          />
        )}
      </motion.div>
    </Container>
  );
}
