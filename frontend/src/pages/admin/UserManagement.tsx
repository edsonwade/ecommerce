import { Box, MenuItem, Select, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { usersApi, type AdminUser } from '@api/users.api';
import type { Role } from '@api/types';
import RoleBadge from '@components/layout/RoleBadge';
import DataTable, { type Column } from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import { useUIStore } from '@stores/ui.store';
import { useAuthStore } from '@stores/auth.store';

const USERS_KEY = ['admin-users'] as const;

export default function UserManagement() {
  const qc = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const currentEmail = useAuthStore((s) => s.email);

  const { data, isLoading } = useQuery({
    queryKey: USERS_KEY,
    queryFn: () => usersApi.list(0, 50),
    staleTime: 60_000,
  });

  const mutate = useMutation({
    mutationFn: ({ id, role }: { id: number; role: Role }) => usersApi.updateRole(id, role),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: USERS_KEY });
      addToast({ message: 'Role updated', variant: 'success' });
    },
    onError: () => addToast({ message: 'Failed to update role', variant: 'error' }),
  });

  const columns: Column<AdminUser>[] = [
    {
      key: 'email',
      label: 'Email',
      render: (r) => <Typography variant="body2" sx={{ fontWeight: 500 }}>{r.email}</Typography>,
    },
    {
      key: 'name',
      label: 'Name',
      render: (r) => (
        <Typography variant="body2" color="text.secondary">
          {r.firstname} {r.lastname}
        </Typography>
      ),
    },
    {
      key: 'tenantId',
      label: 'Tenant',
      render: (r) => (
        <Typography variant="caption" sx={{ fontFamily: 'var(--font-mono)', color: 'text.secondary' }}>
          {r.tenantId}
        </Typography>
      ),
    },
    {
      key: 'role',
      label: 'Role',
      render: (r) => <RoleBadge role={r.role} />,
    },
    {
      key: 'change',
      label: 'Change',
      render: (r) => (
        <Select
          size="small"
          value={r.role}
          disabled={r.email === currentEmail || mutate.isPending}
          onChange={(e) => mutate.mutate({ id: r.id, role: e.target.value as Role })}
          sx={{ minWidth: 120, fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}
        >
          <MenuItem value="USER">USER</MenuItem>
          <MenuItem value="SELLER">SELLER</MenuItem>
          <MenuItem value="ADMIN">ADMIN</MenuItem>
        </Select>
      ),
    },
  ];

  if (isLoading) {
    return (
      <Box sx={{ mt: 3 }}>
        <TableSkeleton rows={6} cols={5} />
      </Box>
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Promote or demote platform users. Your own account cannot be changed here.
      </Typography>
      <DataTable columns={columns} rows={data?.content ?? []} />
    </Box>
  );
}
