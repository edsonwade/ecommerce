import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { motion } from 'framer-motion';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  IconButton,
  InputAdornment,
  TextField,
  Typography,
} from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { authApi } from '@api/auth.api';
import { useUIStore } from '@stores/ui.store';
import { ROUTES } from '@utils/constants';
import { normalizeError } from '@api/client';

const schema = z
  .object({
    newPassword: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string().min(1, 'Please confirm your password'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const addToast = useUIStore((s) => s.addToast);
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      await authApi.resetPassword({
        token,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword,
      });
      addToast({
        message: 'Password reset. Please sign in with your new password.',
        variant: 'success',
      });
      navigate(ROUTES.LOGIN, { replace: true });
    } catch (err) {
      const normalized = normalizeError(err);
      setServerError(
        normalized.status === 400
          ? 'This reset link is invalid or has expired. Please request a new one.'
          : normalized.message
      );
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        px: 2,
        bgcolor: 'background.default',
      }}
    >
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35, ease: [0.2, 0, 0, 1] }}
        style={{ width: '100%', maxWidth: 420 }}
      >
        <Typography
          variant="h3"
          component="div"
          sx={{ mb: 1, color: 'primary.main', fontFamily: 'var(--font-serif)' }}
        >
          Obsidian Market
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
          Choose a new password
        </Typography>

        {!token ? (
          <Box
            sx={{
              bgcolor: 'background.paper',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 2,
              p: 4,
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
            }}
          >
            <Alert severity="error" sx={{ borderRadius: 1 }}>
              This reset link is missing its token. Please request a new one.
            </Alert>
            <Button
              component={Link}
              to={ROUTES.FORGOT_PASSWORD}
              variant="contained"
              size="large"
              fullWidth
            >
              Request a new link
            </Button>
          </Box>
        ) : (
          <Box
            component="form"
            onSubmit={handleSubmit(onSubmit)}
            sx={{
              bgcolor: 'background.paper',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 2,
              p: 4,
              display: 'flex',
              flexDirection: 'column',
              gap: 2.5,
            }}
          >
            {serverError && (
              <Alert severity="error" sx={{ borderRadius: 1 }}>
                {serverError}
              </Alert>
            )}

            <TextField
              {...register('newPassword', { onChange: () => setServerError(null) })}
              label="New password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              autoFocus
              fullWidth
              error={!!errors.newPassword}
              helperText={errors.newPassword?.message ?? 'Minimum 8 characters'}
              slotProps={{
                input: {
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        onClick={() => setShowPassword((v) => !v)}
                        edge="end"
                        aria-label={showPassword ? 'Hide password' : 'Show password'}
                      >
                        {showPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
            />

            <TextField
              {...register('confirmPassword', { onChange: () => setServerError(null) })}
              label="Confirm password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              fullWidth
              error={!!errors.confirmPassword}
              helperText={errors.confirmPassword?.message ?? 'Re-enter your password'}
            />

            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={isSubmitting}
              sx={{ mt: 1 }}
            >
              {isSubmitting ? <CircularProgress size={22} color="inherit" /> : 'Reset password'}
            </Button>
          </Box>
        )}

        <Typography variant="body2" color="text.secondary" sx={{ mt: 3, textAlign: 'center' }}>
          <Link to={ROUTES.LOGIN} style={{ color: 'var(--accent-primary)', textDecoration: 'none' }}>
            Back to sign in
          </Link>
        </Typography>
      </motion.div>
    </Box>
  );
}
