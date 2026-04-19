import { useState } from 'react';
import { Box, Container, Tab, Tabs, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { tenantsApi } from '@api/tenants.api';
import { paymentsApi } from '@api/payments.api';
import { customersApi } from '@api/customers.api';
import { QUERY_KEYS } from '@utils/constants';
import { StatCardSkeleton } from '@components/feedback/LoadingSkeleton';
import { formatCurrency } from '@utils/format';
import UserManagement from './UserManagement';

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <Box sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
      <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>{label}</Typography>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main', mb: 0.5 }}>{value}</Typography>
      {sub && <Typography variant="body2" color="text.secondary">{sub}</Typography>}
    </Box>
  );
}

export default function AdminDashboard() {
  const [tab, setTab] = useState(0);

  const { data: tenants, isLoading: tenantsLoading } = useQuery({
    queryKey: [QUERY_KEYS.TENANTS],
    queryFn: tenantsApi.getAll,
    staleTime: 10 * 60 * 1000,
  });
  const { data: payments, isLoading: paymentsLoading } = useQuery({
    queryKey: [QUERY_KEYS.PAYMENTS],
    queryFn: paymentsApi.getAll,
    staleTime: 5 * 60 * 1000,
  });
  const { data: customers, isLoading: customersLoading } = useQuery({
    queryKey: [QUERY_KEYS.CUSTOMERS],
    queryFn: customersApi.getAll,
    staleTime: 5 * 60 * 1000,
  });

  const isLoading = tenantsLoading || paymentsLoading || customersLoading;
  const totalRevenue = payments?.reduce((s, p) => s + p.amount, 0) ?? 0;
  const activeTenants = tenants?.filter((t) => t.status === 'ACTIVE').length ?? 0;

  return (
    <Container maxWidth="lg" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 1 }}>Admin Dashboard</Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>Platform overview</Typography>

        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          sx={{ mb: 3, borderBottom: '1px solid', borderColor: 'divider' }}
        >
          <Tab label="Overview" />
          <Tab label="Users" />
        </Tabs>

        {tab === 0 && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }, gap: 2.5, mb: 6 }}>
            {isLoading ? (
              [1, 2, 3, 4].map((i) => <StatCardSkeleton key={i} />)
            ) : (
              <>
                <StatCard label="TOTAL TENANTS" value={String(tenants?.length ?? 0)} sub={`${activeTenants} active`} />
                <StatCard label="TOTAL REVENUE" value={formatCurrency(totalRevenue)} />
                <StatCard label="TOTAL CUSTOMERS" value={String(customers?.length ?? 0)} />
                <StatCard label="TOTAL PAYMENTS" value={String(payments?.length ?? 0)} />
              </>
            )}
          </Box>
        )}

        {tab === 1 && <UserManagement />}
      </motion.div>
    </Container>
  );
}
