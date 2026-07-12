import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  MenuItem,
  Select,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { DeleteOutlined, Edit, PersonAdd } from '@mui/icons-material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  usersApi,
  type AdminUser,
  type AdminCreateUserPayload,
  type AdminUpdateUserPayload,
} from '@api/users.api';
import type { Role } from '@api/types';
import { normalizeError } from '@api/client';
import RoleBadge from '@components/layout/RoleBadge';
import DataTable, { type Column } from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import ConfirmDialog from '@components/feedback/ConfirmDialog';
import { useUIStore } from '@stores/ui.store';
import { useAuthStore } from '@stores/auth.store';

const USERS_KEY = ['admin-users'] as const;

const createSchema = z.object({
  firstname: z.string().min(1, 'First name is required'),
  lastname: z.string().min(1, 'Last name is required'),
  email: z.string().email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  role: z.enum(['USER', 'SELLER', 'ADMIN']),
});

const editSchema = z.object({
  firstname: z.string().min(1, 'First name is required'),
  lastname: z.string().min(1, 'Last name is required'),
  email: z.string().email('Enter a valid email'),
});

type CreateFormValues = z.infer<typeof createSchema>;
type EditFormValues = z.infer<typeof editSchema>;

export default function UserManagement() {
  const qc = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const currentEmail = useAuthStore((s) => s.email);

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<AdminUser | null>(null);
  const [statusTarget, setStatusTarget] = useState<AdminUser | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminUser | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: USERS_KEY,
    queryFn: ({ signal }) => usersApi.list(0, 50, signal),
    staleTime: 60_000,
  });

  const {
    register: registerCreate,
    handleSubmit: handleCreateSubmit,
    control: createControl,
    reset: resetCreate,
    setError: setCreateError,
    formState: { errors: createErrors, isSubmitting: isCreating },
  } = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { firstname: '', lastname: '', email: '', password: '', role: 'USER' },
  });

  const {
    register: registerEdit,
    handleSubmit: handleEditSubmit,
    reset: resetEdit,
    setError: setEditError,
    formState: { errors: editErrors, isSubmitting: isEditing },
  } = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: { firstname: '', lastname: '', email: '' },
  });

  const roleMut = useMutation({
    mutationFn: ({ id, role }: { id: number; role: Role }) => usersApi.updateRole(id, role),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: USERS_KEY });
      addToast({ message: 'Role updated', variant: 'success' });
    },
    onError: () => addToast({ message: 'Failed to update role', variant: 'error' }),
  });

  const createMut = useMutation({
    mutationFn: (payload: AdminCreateUserPayload) => usersApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: USERS_KEY }),
  });

  const updateMut = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AdminUpdateUserPayload }) =>
      usersApi.update(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: USERS_KEY }),
  });

  const statusMut = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      usersApi.setStatus(id, enabled),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: USERS_KEY });
      addToast({ message: vars.enabled ? 'User activated' : 'User deactivated', variant: 'success' });
      setStatusTarget(null);
    },
    onError: (err: unknown) => {
      addToast({ message: normalizeError(err).message, variant: 'error' });
      setStatusTarget(null);
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => usersApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: USERS_KEY });
      addToast({ message: 'User deleted', variant: 'success' });
      setDeleteTarget(null);
    },
    onError: (err: unknown) => {
      addToast({ message: normalizeError(err).message, variant: 'error' });
      setDeleteTarget(null);
    },
  });

  const openCreate = () => {
    resetCreate({ firstname: '', lastname: '', email: '', password: '', role: 'USER' });
    setCreateOpen(true);
  };

  const openEdit = (user: AdminUser) => {
    resetEdit({ firstname: user.firstname, lastname: user.lastname, email: user.email });
    setEditTarget(user);
  };

  const onCreateSubmit = async (values: CreateFormValues) => {
    try {
      await createMut.mutateAsync(values);
      addToast({ message: 'User created', variant: 'success' });
      setCreateOpen(false);
    } catch (err) {
      const normalized = normalizeError(err);
      if (normalized.fieldErrors) {
        Object.entries(normalized.fieldErrors).forEach(([field, message]) => {
          setCreateError(field as keyof CreateFormValues, { message });
        });
      } else if (normalized.status === 409) {
        setCreateError('email', { message: 'This email is already registered' });
      } else {
        addToast({ message: normalized.message, variant: 'error' });
      }
    }
  };

  const onEditSubmit = async (values: EditFormValues) => {
    if (!editTarget) return;
    try {
      await updateMut.mutateAsync({ id: editTarget.id, payload: values });
      addToast({ message: 'User updated', variant: 'success' });
      setEditTarget(null);
    } catch (err) {
      const normalized = normalizeError(err);
      if (normalized.fieldErrors) {
        Object.entries(normalized.fieldErrors).forEach(([field, message]) => {
          setEditError(field as keyof EditFormValues, { message });
        });
      } else if (normalized.status === 409) {
        setEditError('email', { message: 'This email is already registered' });
      } else {
        addToast({ message: normalized.message, variant: 'error' });
      }
    }
  };

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
          disabled={r.email === currentEmail || roleMut.isPending}
          onChange={(e) => roleMut.mutate({ id: r.id, role: e.target.value as Role })}
          sx={{ minWidth: 120, fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}
        >
          <MenuItem value="USER">USER</MenuItem>
          <MenuItem value="SELLER">SELLER</MenuItem>
          <MenuItem value="ADMIN">ADMIN</MenuItem>
        </Select>
      ),
    },
    {
      key: 'status',
      label: 'Status',
      render: (r) => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Switch
            size="small"
            checked={r.accountEnabled}
            disabled={r.email === currentEmail || statusMut.isPending}
            onChange={() => setStatusTarget(r)}
            color="primary"
          />
          <Chip
            size="small"
            variant="outlined"
            label={r.accountEnabled ? 'Active' : 'Disabled'}
            color={r.accountEnabled ? 'success' : 'default'}
          />
        </Box>
      ),
    },
    {
      key: 'actions',
      label: 'Actions',
      align: 'right',
      render: (r) => (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
          <IconButton
            size="small"
            onClick={() => openEdit(r)}
            aria-label="Edit user"
            sx={{ color: 'text.secondary' }}
          >
            <Edit fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            disabled={r.email === currentEmail}
            onClick={() => setDeleteTarget(r)}
            aria-label="Delete user"
            sx={{ color: 'error.main' }}
          >
            <DeleteOutlined fontSize="small" />
          </IconButton>
        </Box>
      ),
    },
  ];

  if (isLoading) {
    return (
      <Box sx={{ mt: 3 }}>
        <TableSkeleton rows={6} cols={7} />
      </Box>
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          flexWrap: 'wrap',
          mb: 2,
        }}
      >
        <Typography variant="body2" color="text.secondary">
          Create, edit, activate or remove platform users. Your own account cannot be changed here.
        </Typography>
        <Button variant="contained" startIcon={<PersonAdd />} onClick={openCreate}>
          Create user
        </Button>
      </Box>

      <DataTable columns={columns} rows={data?.content ?? []} />

      {/* Create user dialog */}
      <Dialog
        open={createOpen}
        onClose={() => !isCreating && setCreateOpen(false)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle sx={{ fontFamily: 'var(--font-serif)' }}>Create user</DialogTitle>
        <Box component="form" onSubmit={handleCreateSubmit(onCreateSubmit)} noValidate>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pt: 1 }}>
            <Grid container spacing={2}>
              <Grid size={6}>
                <TextField
                  {...registerCreate('firstname')}
                  label="First name"
                  autoFocus
                  fullWidth
                  error={!!createErrors.firstname}
                  helperText={createErrors.firstname?.message}
                />
              </Grid>
              <Grid size={6}>
                <TextField
                  {...registerCreate('lastname')}
                  label="Last name"
                  fullWidth
                  error={!!createErrors.lastname}
                  helperText={createErrors.lastname?.message}
                />
              </Grid>
            </Grid>
            <TextField
              {...registerCreate('email')}
              label="Email"
              type="email"
              autoComplete="off"
              fullWidth
              error={!!createErrors.email}
              helperText={createErrors.email?.message}
            />
            <TextField
              {...registerCreate('password')}
              label="Password"
              type="password"
              autoComplete="new-password"
              fullWidth
              error={!!createErrors.password}
              helperText={createErrors.password?.message ?? 'Minimum 8 characters'}
            />
            <Controller
              name="role"
              control={createControl}
              render={({ field }) => (
                <TextField
                  {...field}
                  select
                  label="Role"
                  fullWidth
                  error={!!createErrors.role}
                  helperText={createErrors.role?.message}
                >
                  <MenuItem value="USER">USER</MenuItem>
                  <MenuItem value="SELLER">SELLER</MenuItem>
                  <MenuItem value="ADMIN">ADMIN</MenuItem>
                </TextField>
              )}
            />
          </DialogContent>
          <DialogActions sx={{ p: 2.5, pt: 1.5, gap: 1 }}>
            <Button variant="outlined" onClick={() => setCreateOpen(false)} disabled={isCreating}>
              Cancel
            </Button>
            <Button type="submit" variant="contained" disabled={isCreating}>
              {isCreating ? <CircularProgress size={20} color="inherit" /> : 'Create'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      {/* Edit user dialog */}
      <Dialog
        open={!!editTarget}
        onClose={() => !isEditing && setEditTarget(null)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle sx={{ fontFamily: 'var(--font-serif)' }}>Edit user</DialogTitle>
        <Box component="form" onSubmit={handleEditSubmit(onEditSubmit)} noValidate>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pt: 1 }}>
            <Grid container spacing={2}>
              <Grid size={6}>
                <TextField
                  {...registerEdit('firstname')}
                  label="First name"
                  autoFocus
                  fullWidth
                  error={!!editErrors.firstname}
                  helperText={editErrors.firstname?.message}
                />
              </Grid>
              <Grid size={6}>
                <TextField
                  {...registerEdit('lastname')}
                  label="Last name"
                  fullWidth
                  error={!!editErrors.lastname}
                  helperText={editErrors.lastname?.message}
                />
              </Grid>
            </Grid>
            <TextField
              {...registerEdit('email')}
              label="Email"
              type="email"
              fullWidth
              error={!!editErrors.email}
              helperText={editErrors.email?.message ?? 'Changing the email signs the user out everywhere'}
            />
          </DialogContent>
          <DialogActions sx={{ p: 2.5, pt: 1.5, gap: 1 }}>
            <Button variant="outlined" onClick={() => setEditTarget(null)} disabled={isEditing}>
              Cancel
            </Button>
            <Button type="submit" variant="contained" disabled={isEditing}>
              {isEditing ? <CircularProgress size={20} color="inherit" /> : 'Save changes'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      {/* Activate / deactivate confirmation */}
      <ConfirmDialog
        open={!!statusTarget}
        title={statusTarget?.accountEnabled ? 'Deactivate user?' : 'Activate user?'}
        description={
          statusTarget?.accountEnabled
            ? `${statusTarget.email} will be signed out everywhere and unable to log in until reactivated.`
            : `${statusTarget?.email ?? ''} will be able to log in again.`
        }
        confirmLabel={statusTarget?.accountEnabled ? 'Deactivate' : 'Activate'}
        destructive={!!statusTarget?.accountEnabled}
        loading={statusMut.isPending}
        onConfirm={() =>
          statusTarget &&
          statusMut.mutate({ id: statusTarget.id, enabled: !statusTarget.accountEnabled })
        }
        onCancel={() => setStatusTarget(null)}
      />

      {/* Delete confirmation */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete user?"
        description={`${deleteTarget?.email ?? ''} will be permanently anonymized and signed out everywhere. This cannot be undone.`}
        confirmLabel="Delete"
        destructive
        loading={deleteMut.isPending}
        onConfirm={() => deleteTarget && deleteMut.mutate(deleteTarget.id)}
        onCancel={() => setDeleteTarget(null)}
      />
    </Box>
  );
}
