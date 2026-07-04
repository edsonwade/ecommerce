import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Button, CircularProgress, Container, Dialog, DialogActions, DialogContent,
  DialogContentText, DialogTitle, Divider, IconButton, InputAdornment, Paper,
  TextField, Typography,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi } from '@api/auth.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';

const identitySchema = z
  .object({
    firstname: z.string().min(1, 'Required'),
    lastname: z.string().min(1, 'Required'),
    email: z.string().email('Invalid email'),
    currentPassword: z.string().optional(),
  });
type IdentityValues = z.infer<typeof identitySchema>;

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Required'),
    newPassword: z.string().min(8, 'At least 8 characters'),
    confirmPassword: z.string().min(1, 'Required'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });
type PasswordValues = z.infer<typeof passwordSchema>;

export default function AccountSettingsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const { role, setTokens, setEmail, clearAuth } = useAuthStore();

  const { data: account, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.ACCOUNT],
    queryFn: () => authApi.getAccount(),
  });

  // ── Identity form ──
  const identityForm = useForm<IdentityValues>({ resolver: zodResolver(identitySchema) });
  useEffect(() => {
    if (account) {
      identityForm.reset({
        firstname: account.firstname,
        lastname: account.lastname,
        email: account.email,
        currentPassword: '',
      });
    }
  }, [account]); // eslint-disable-line react-hooks/exhaustive-deps

  const watchedEmail = identityForm.watch('email');
  const emailChanged = !!account && !!watchedEmail
    && watchedEmail.toLowerCase() !== account.email.toLowerCase();

  const updateIdentity = useMutation({
    mutationFn: (v: IdentityValues) =>
      authApi.updateAccount({
        firstname: v.firstname,
        lastname: v.lastname,
        email: v.email,
        currentPassword: emailChanged ? v.currentPassword : undefined,
      }),
    onSuccess: (res) => {
      if (res.tokens) {
        // Email changed: the old JWT subject is dead — adopt the fresh pair immediately.
        setTokens(res.tokens.accessToken, res.tokens.refreshToken);
        setEmail(res.tokens.email);
      }
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.ACCOUNT] });
      addToast({ message: 'Account updated', variant: 'success' });
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      addToast({
        message: err.response?.data?.message ?? 'Failed to update account',
        variant: 'error',
      });
    },
  });

  // ── Password form ──
  const [showPw, setShowPw] = useState(false);
  const passwordForm = useForm<PasswordValues>({ resolver: zodResolver(passwordSchema) });
  const changePassword = useMutation({
    mutationFn: (v: PasswordValues) => authApi.changePassword(v),
    onSuccess: (tokens) => {
      setTokens(tokens.accessToken, tokens.refreshToken);
      passwordForm.reset({ currentPassword: '', newPassword: '', confirmPassword: '' });
      addToast({ message: 'Password changed', variant: 'success' });
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      addToast({
        message: err.response?.data?.message ?? 'Failed to change password',
        variant: 'error',
      });
    },
  });

  // ── Danger zone (USER only) ──
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const deleteAccount = useMutation({
    mutationFn: () => authApi.deleteAccount(deletePassword),
    onSuccess: () => {
      clearAuth();
      addToast({ message: 'Your account has been deleted', variant: 'success' });
      navigate(ROUTES.HOME);
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      addToast({
        message: err.response?.data?.message ?? 'Failed to delete account',
        variant: 'error',
      });
    },
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 5 }}>
        Account settings
      </Typography>

      {/* ── Identity ── */}
      <Paper sx={{ p: 3, mb: 4 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Identity</Typography>
        <Box
          component="form"
          onSubmit={identityForm.handleSubmit((v) => updateIdentity.mutateAsync(v))}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}
        >
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField
              {...identityForm.register('firstname')}
              label="First name"
              error={!!identityForm.formState.errors.firstname}
              helperText={identityForm.formState.errors.firstname?.message}
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
            <TextField
              {...identityForm.register('lastname')}
              label="Last name"
              error={!!identityForm.formState.errors.lastname}
              helperText={identityForm.formState.errors.lastname?.message}
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
          </Box>
          <TextField
            {...identityForm.register('email')}
            label="Email (used to sign in)"
            type="email"
            error={!!identityForm.formState.errors.email}
            helperText={identityForm.formState.errors.email?.message}
            slotProps={{ inputLabel: { shrink: true } }}
            fullWidth
          />
          {emailChanged && (
            <TextField
              {...identityForm.register('currentPassword')}
              label="Current password (required to change email)"
              type="password"
              helperText="Changing your sign-in email requires your current password."
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
          )}
          <Button
            type="submit"
            variant="contained"
            disabled={identityForm.formState.isSubmitting}
          >
            {identityForm.formState.isSubmitting
              ? <CircularProgress size={22} color="inherit" />
              : 'Save identity'}
          </Button>
        </Box>
      </Paper>

      {/* ── Change password ── */}
      <Paper sx={{ p: 3, mb: 4 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Change password</Typography>
        <Box
          component="form"
          onSubmit={passwordForm.handleSubmit((v) => changePassword.mutateAsync(v))}
          sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}
        >
          <TextField
            {...passwordForm.register('currentPassword')}
            label="Current password"
            type={showPw ? 'text' : 'password'}
            error={!!passwordForm.formState.errors.currentPassword}
            helperText={passwordForm.formState.errors.currentPassword?.message}
            fullWidth
            slotProps={{
              inputLabel: { shrink: true },
              input: {
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton onClick={() => setShowPw((s) => !s)} edge="end" size="small">
                      {showPw ? <VisibilityOff /> : <Visibility />}
                    </IconButton>
                  </InputAdornment>
                ),
              },
            }}
          />
          <TextField
            {...passwordForm.register('newPassword')}
            label="New password"
            type={showPw ? 'text' : 'password'}
            error={!!passwordForm.formState.errors.newPassword}
            helperText={passwordForm.formState.errors.newPassword?.message}
            slotProps={{ inputLabel: { shrink: true } }}
            fullWidth
          />
          <TextField
            {...passwordForm.register('confirmPassword')}
            label="Confirm new password"
            type={showPw ? 'text' : 'password'}
            error={!!passwordForm.formState.errors.confirmPassword}
            helperText={passwordForm.formState.errors.confirmPassword?.message}
            slotProps={{ inputLabel: { shrink: true } }}
            fullWidth
          />
          <Button
            type="submit"
            variant="contained"
            disabled={passwordForm.formState.isSubmitting}
          >
            {passwordForm.formState.isSubmitting
              ? <CircularProgress size={22} color="inherit" />
              : 'Change password'}
          </Button>
        </Box>
      </Paper>

      {/* ── Danger zone — customers only ── */}
      {role === 'USER' && (
        <Paper sx={{ p: 3, borderColor: 'error.main', borderWidth: 1, borderStyle: 'solid' }}>
          <Typography variant="h6" color="error" sx={{ mb: 1 }}>Danger zone</Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
            Deleting your account signs you out everywhere and removes your profile.
            Your past orders remain on record. This cannot be undone.
          </Typography>
          <Button variant="outlined" color="error" onClick={() => setDeleteOpen(true)}>
            Delete my account
          </Button>
          <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)}>
            <DialogTitle>Delete your account?</DialogTitle>
            <DialogContent>
              <DialogContentText sx={{ mb: 2 }}>
                Enter your password to confirm. This action is permanent.
              </DialogContentText>
              <TextField
                autoFocus
                label="Password"
                type="password"
                value={deletePassword}
                onChange={(e) => setDeletePassword(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
                fullWidth
              />
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setDeleteOpen(false)}>Cancel</Button>
              <Button
                color="error"
                variant="contained"
                disabled={!deletePassword || deleteAccount.isPending}
                onClick={() => deleteAccount.mutate()}
              >
                {deleteAccount.isPending
                  ? <CircularProgress size={20} color="inherit" />
                  : 'Delete account'}
              </Button>
            </DialogActions>
          </Dialog>
        </Paper>
      )}
      <Divider sx={{ mt: 4, opacity: 0 }} />
    </Container>
  );
}
