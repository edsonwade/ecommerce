import { Container, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { customersApi } from '@api/customers.api';
import { QUERY_KEYS } from '@utils/constants';
import DataTable from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import type { CustomerResponse } from '@api/types';

export default function UsersPage() {
  const { data: customers, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.CUSTOMERS],
    queryFn: customersApi.getAll,
    staleTime: 5 * 60 * 1000,
  });

  const COLUMNS = [
    { key: 'id', label: 'ID', render: (r: CustomerResponse) => <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'text.secondary' }}>{r.id.slice(0, 8)}…</Typography> },
    { key: 'firstname', label: 'Name', render: (r: CustomerResponse) => <Typography variant="body2" sx={{ fontWeight: 500 }}>{r.firstname} {r.lastname}</Typography> },
    { key: 'email', label: 'Email', render: (r: CustomerResponse) => <Typography variant="body2" color="text.secondary">{r.email}</Typography> },
    { key: 'city', label: 'Location', render: (r: CustomerResponse) => <Typography variant="caption" color="text.secondary">{r.address.city}, {r.address.country}</Typography> },
  ];

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 4 }}>Users</Typography>
        {isLoading ? (
          <TableSkeleton rows={8} cols={4} />
        ) : (
          <DataTable
            columns={COLUMNS}
            rows={(customers ?? []).map((c) => ({ ...c, id: c.id }))}
          />
        )}
      </motion.div>
    </Container>
  );
}
