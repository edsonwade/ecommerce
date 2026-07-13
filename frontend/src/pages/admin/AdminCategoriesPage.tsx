import { useState } from 'react';
import {
  Box,
  Button,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Add } from '@mui/icons-material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { categoriesApi } from '@api/categories.api';
import type { CategoryResponse } from '@api/types';
import { normalizeError } from '@api/client';
import DataTable, { type Column } from '@components/data-display/DataTable';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import ConfirmDialog from '@components/feedback/ConfirmDialog';
import { useUIStore } from '@stores/ui.store';
import { QUERY_KEYS } from '@utils/constants';

const ADMIN_CATEGORIES_KEY = ['admin-categories'] as const;

/**
 * Fase 4 — /admin/categories: simple CRUD over the category catalogue. Create/edit share
 * one form dialog; delete confirms first and surfaces the backend's 409 ("still referenced
 * by products") as a plain toast. Every mutation also invalidates the shared CATEGORIES
 * query so the storefront + ProductForm dropdowns refresh with the change.
 */
export default function AdminCategoriesPage() {
  const qc = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);

  // null = closed; { id: null } = create; { id: number } = edit
  const [editing, setEditing] = useState<Partial<CategoryResponse> | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CategoryResponse | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ADMIN_CATEGORIES_KEY,
    queryFn: ({ signal }) => categoriesApi.getAll(signal),
    staleTime: 30_000,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ADMIN_CATEGORIES_KEY });
    // The storefront + product-form dropdowns read the shared CATEGORIES key.
    qc.invalidateQueries({ queryKey: [QUERY_KEYS.CATEGORIES] });
  };

  const isEdit = editing?.id != null;

  const saveMut = useMutation({
    mutationFn: () => {
      const payload = { name: name.trim(), description: description.trim() || undefined };
      return isEdit
        ? categoriesApi.update(editing!.id as number, payload)
        : categoriesApi.create(payload);
    },
    onSuccess: () => {
      invalidate();
      addToast({
        message: isEdit ? 'Category updated' : 'Category created',
        variant: 'success',
      });
      closeForm();
    },
    onError: (err: unknown) => addToast({ message: normalizeError(err).message, variant: 'error' }),
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => categoriesApi.remove(id),
    onSuccess: () => {
      invalidate();
      addToast({ message: 'Category deleted', variant: 'success' });
      setDeleteTarget(null);
    },
    onError: (err: unknown) => {
      // A category still referenced by products comes back as 409 with a clear message.
      addToast({ message: normalizeError(err).message, variant: 'error' });
      setDeleteTarget(null);
    },
  });

  const openCreate = () => {
    setEditing({ id: undefined });
    setName('');
    setDescription('');
  };

  const openEdit = (row: CategoryResponse) => {
    setEditing(row);
    setName(row.name);
    setDescription(row.description ?? '');
  };

  const closeForm = () => setEditing(null);

  const COLUMNS: Column<CategoryResponse>[] = [
    {
      key: 'id',
      label: 'ID',
      render: (r) => (
        <Typography sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'text.secondary' }}>
          {r.id}
        </Typography>
      ),
    },
    {
      key: 'name',
      label: 'Name',
      render: (r) => <Typography variant="body2" sx={{ fontWeight: 500 }}>{r.name}</Typography>,
    },
    {
      key: 'description',
      label: 'Description',
      render: (r) => (
        <Typography variant="body2" color="text.secondary">{r.description || '—'}</Typography>
      ),
    },
  ];

  return (
    <Container maxWidth="lg" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)' }}>Categories</Typography>
          <Button variant="contained" startIcon={<Add />} onClick={openCreate}>
            Add category
          </Button>
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Manage the category catalogue. A category still used by products cannot be deleted.
        </Typography>

        {isLoading ? (
          <TableSkeleton rows={6} cols={3} />
        ) : (
          <DataTable
            columns={COLUMNS}
            rows={data ?? []}
            onEdit={openEdit}
            onDelete={setDeleteTarget}
          />
        )}

        {/* Create / edit form */}
        <Dialog open={!!editing} onClose={closeForm} maxWidth="xs" fullWidth>
          <DialogTitle sx={{ fontFamily: 'var(--font-serif)' }}>
            {isEdit ? 'Edit category' : 'New category'}
          </DialogTitle>
          <DialogContent>
            <Stack spacing={2.5} sx={{ mt: 1 }}>
              <TextField
                label="Name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                fullWidth
                autoFocus
                required
              />
              <TextField
                label="Description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                fullWidth
                multiline
                minRows={2}
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ p: 2.5, pt: 1.5, gap: 1 }}>
            <Button variant="outlined" onClick={closeForm} disabled={saveMut.isPending}>
              Cancel
            </Button>
            <Button
              variant="contained"
              onClick={() => saveMut.mutate()}
              disabled={saveMut.isPending || !name.trim()}
            >
              {isEdit ? 'Save' : 'Create'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Delete confirmation */}
        <ConfirmDialog
          open={!!deleteTarget}
          title="Delete category?"
          description={
            deleteTarget
              ? `"${deleteTarget.name}" will be removed. If any product still uses it, the delete is rejected.`
              : undefined
          }
          confirmLabel="Delete"
          destructive
          loading={deleteMut.isPending}
          onConfirm={() => deleteTarget && deleteMut.mutate(deleteTarget.id)}
          onCancel={() => setDeleteTarget(null)}
        />
      </motion.div>
    </Container>
  );
}
