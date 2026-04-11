import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, CircularProgress, Container, Divider,
  FormControl, InputLabel, MenuItem, Select, Switch, Tab, Tabs, Typography,
} from '@mui/material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowBack } from '@mui/icons-material';
import { motion } from 'framer-motion';
import { tenantsApi } from '@api/tenants.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useUIStore } from '@stores/ui.store';
import { formatDateTime } from '@utils/format';
import type { TenantPlan, TenantResponse, TenantStatus } from '@api/types';

const STATUS_COLOR: Record<TenantStatus, string> = {
  ACTIVE: 'var(--status-success)',
  SUSPENDED: 'var(--status-warning)',
  CANCELLED: 'var(--status-error)',
};

const PLANS: TenantPlan[] = ['FREE', 'STARTER', 'GROWTH', 'ENTERPRISE'];

export default function TenantDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const [tab, setTab] = useState(0);
  const [selectedPlan, setSelectedPlan] = useState<TenantPlan>('FREE');

  const { data: tenant, isLoading } = useQuery<TenantResponse>({
    queryKey: [QUERY_KEYS.TENANT, id],
    queryFn: () => tenantsApi.getById(id!),
    enabled: !!id,
  });

  // Sync selectedPlan when tenant data arrives
  useEffect(() => {
    if (tenant) setSelectedPlan(tenant.plan);
  }, [tenant]);

  const { data: flags } = useQuery({
    queryKey: [QUERY_KEYS.TENANT_FLAGS, id],
    queryFn: () => tenantsApi.getFlags(id!),
    enabled: !!id && tab === 1,
  });

  const { mutate: changePlan, isPending: changingPlan } = useMutation({
    mutationFn: (plan: TenantPlan) => tenantsApi.changePlan(id!, plan),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.TENANT, id] });
      addToast({ message: 'Plan updated', variant: 'success' });
    },
  });

  const { mutate: toggleStatus } = useMutation({
    mutationFn: () =>
      tenant?.status === 'ACTIVE' ? tenantsApi.suspend(id!) : tenantsApi.reactivate(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.TENANT, id] });
      addToast({ message: `Tenant ${tenant?.status === 'ACTIVE' ? 'suspended' : 'reactivated'}`, variant: 'info' });
    },
  });

  const { mutate: toggleFlag } = useMutation({
    mutationFn: ({ flagName, enabled }: { flagName: string; enabled: boolean }) =>
      tenantsApi.setFlag(id!, flagName, { enabled }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.TENANT_FLAGS, id] });
    },
  });

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}><CircularProgress /></Box>;
  }

  if (!tenant) {
    return <Container maxWidth="md" sx={{ py: 8 }}><Alert severity="error">Tenant not found.</Alert></Container>;
  }

  return (
    <Container maxWidth="lg" sx={{ py: 2 }}>
      <Button component={Link} to={ROUTES.ADMIN_TENANTS} startIcon={<ArrowBack />} sx={{ mb: 3, color: 'text.secondary' }}>
        Back to tenants
      </Button>

      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 4, flexWrap: 'wrap', gap: 2 }}>
          <Box>
            <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 0.5 }}>{tenant.name}</Typography>
            <Typography sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main', fontSize: '0.9rem' }}>
              {tenant.slug}
            </Typography>
          </Box>
          <Chip label={tenant.status} sx={{ bgcolor: STATUS_COLOR[tenant.status], color: '#fff', fontFamily: 'var(--font-mono)' }} />
        </Box>

        <Tabs value={tab} onChange={(_, v: number) => setTab(v)} sx={{ mb: 4, borderBottom: '1px solid', borderColor: 'divider' }}>
          <Tab label="Overview" />
          <Tab label="Feature Flags" />
          <Tab label="Usage" />
        </Tabs>

        {/* Overview tab */}
        {tab === 0 && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 4 }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
              {[
                { label: 'Tenant ID', value: tenant.tenantId, mono: true },
                { label: 'Contact', value: tenant.contactEmail },
                { label: 'Rate limit', value: `${tenant.rateLimit} req/min`, mono: true },
                { label: 'Storage quota', value: `${tenant.storageQuota} MB`, mono: true },
                { label: 'Created', value: formatDateTime(tenant.createdAt) },
              ].map(({ label, value, mono }) => (
                <Box key={label}>
                  <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 0.25 }}>{label.toUpperCase()}</Typography>
                  <Typography variant="body2" sx={mono ? { fontFamily: 'var(--font-mono)', color: 'primary.main' } : {}}>
                    {value}
                  </Typography>
                </Box>
              ))}
            </Box>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>CHANGE PLAN</Typography>
                <Box sx={{ display: 'flex', gap: 2 }}>
                  <FormControl size="small" sx={{ minWidth: 140 }}>
                    <InputLabel>Plan</InputLabel>
                    <Select value={selectedPlan} label="Plan" onChange={(e) => setSelectedPlan(e.target.value as TenantPlan)}>
                      {PLANS.map((p) => <MenuItem key={p} value={p}>{p}</MenuItem>)}
                    </Select>
                  </FormControl>
                  <Button variant="contained" size="small" onClick={() => changePlan(selectedPlan)} disabled={changingPlan || selectedPlan === tenant.plan}>
                    Apply
                  </Button>
                </Box>
              </Box>

              <Divider />

              <Box>
                <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>TENANT STATUS</Typography>
                <Button
                  variant="outlined"
                  color={tenant.status === 'ACTIVE' ? 'warning' : 'success'}
                  size="small"
                  onClick={() => toggleStatus()}
                >
                  {tenant.status === 'ACTIVE' ? 'Suspend tenant' : 'Reactivate tenant'}
                </Button>
              </Box>
            </Box>
          </Box>
        )}

        {/* Feature Flags tab */}
        {tab === 1 && (
          <Box>
            {!flags || flags.length === 0 ? (
              <Typography variant="body2" color="text.secondary">No feature flags configured.</Typography>
            ) : (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
                {flags.map((flag, idx) => (
                  <Box
                    key={flag.flagName}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      py: 2.5,
                      borderBottom: idx < flags.length - 1 ? '1px solid' : 'none',
                      borderColor: 'divider',
                    }}
                  >
                    <Box>
                      <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)', fontWeight: 500, mb: 0.25 }}>
                        {flag.flagName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">{flag.description}</Typography>
                    </Box>
                    <Switch
                      checked={flag.enabled}
                      onChange={(e) => toggleFlag({ flagName: flag.flagName, enabled: e.target.checked })}
                      color="primary"
                    />
                  </Box>
                ))}
              </Box>
            )}
          </Box>
        )}

        {/* Usage tab */}
        {tab === 2 && (
          <Box>
            <Typography variant="body2" color="text.secondary">
              Usage analytics available via the Analytics page with date range selection.
            </Typography>
          </Box>
        )}
      </motion.div>
    </Container>
  );
}
