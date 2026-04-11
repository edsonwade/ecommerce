import { useState } from 'react';
import { Box, Button, Container, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { tenantsApi } from '@api/tenants.api';
import { QUERY_KEYS } from '@utils/constants';
import { formatDateTime } from '@utils/format';

export default function AnalyticsPage() {
  const [tenantId, setTenantId] = useState('');
  const [startDate, setStartDate] = useState('2024-01-01');
  const [endDate, setEndDate] = useState(new Date().toISOString().slice(0, 10));
  const [query, setQuery] = useState<{ id: string; start: string; end: string } | null>(null);

  const { data: usageData, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.TENANT_USAGE, query?.id, query?.start, query?.end],
    queryFn: () => tenantsApi.getUsageRange(query!.id, query!.start, query!.end),
    enabled: !!query,
  });

  const chartData = usageData?.map((u) => ({
    date: formatDateTime(u.recordedAt),
    value: u.value,
    metric: u.metricName,
  })) ?? [];

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 4 }}>Analytics</Typography>

        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 4, alignItems: 'flex-end' }}>
          <TextField label="Tenant ID" size="small" value={tenantId} onChange={(e) => setTenantId(e.target.value)} sx={{ width: 280 }} />
          <TextField label="Start date" type="date" size="small" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          <TextField label="End date" type="date" size="small" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          <Button variant="contained" onClick={() => setQuery({ id: tenantId, start: startDate, end: endDate })} disabled={!tenantId}>
            Load data
          </Button>
        </Box>

        {isLoading && (
          <Typography variant="body2" color="text.secondary">Loading usage data…</Typography>
        )}

        {!isLoading && chartData.length > 0 && (
          <Box sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
            <Typography variant="h6" sx={{ fontFamily: 'var(--font-serif)', mb: 3 }}>
              Usage over time
            </Typography>
            <ResponsiveContainer width="100%" height={320}>
              <LineChart data={chartData}>
                <CartesianGrid stroke="var(--border-default)" strokeDasharray="3 3" />
                <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} />
                <YAxis tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} />
                <Tooltip
                  contentStyle={{
                    background: 'var(--surface-overlay)',
                    border: '1px solid var(--border-default)',
                    borderRadius: 4,
                    color: 'var(--text-primary)',
                  }}
                />
                <Line type="monotone" dataKey="value" stroke="var(--accent-primary)" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </Box>
        )}

        {!isLoading && query && chartData.length === 0 && (
          <Typography variant="body2" color="text.secondary">No usage data found for the selected range.</Typography>
        )}
      </motion.div>
    </Container>
  );
}
