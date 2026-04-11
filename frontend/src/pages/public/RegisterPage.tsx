import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { motion } from 'framer-motion';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Grid,
  IconButton,
  InputAdornment,
  TextField,
  Typography,
} from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { authApi } from '@api/auth.api';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { ROUTES } from '@utils/constants';
import { normalizeError } from '@api/client';

const schema = z.object({
  firstname: z.string().min(1, 'First name is required'),
  lastname: z.string().min(1, 'Last name is required'),
  email: z.string().email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
});

type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const addToast = useUIStore((s) => s.addToast);
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      const res = await authApi.register(values);
      setAuth({
        accessToken: res.accessToken,
        refreshToken: res.refreshToken,
        userId: res.userId,
        email: res.email,
        role: res.role,
        tenantId: res.tenantId,
      });
      addToast({ message: 'Account created successfully', variant: 'success' });
      navigate(ROUTES.ACCOUNT, { replace: true });
    } catch (err) {
      const normalized = normalizeError(err);
      if (normalized.fieldErrors) {
        Object.entries(normalized.fieldErrors).forEach(([field, message]) => {
          setError(field as keyof FormValues, { message });
        });
      } else if (normalized.status === 409) {
        setError('email', { message: 'This email is already registered' });
      } else {
        setServerError(normalized.message);
      }
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
        style={{ width: '100%', maxWidth: 480 }}
      >
        <Typography
          variant="h3"
          component="div"
          sx={{ mb: 1, color: 'primary.main', fontFamily: 'var(--font-serif)' }}
        >
          Obsidian Market
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
          Create your account
        </Typography>

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

          <Grid container spacing={2}>
            <Grid size={6}>
              <TextField
                {...register('firstname')}
                label="First name"
                autoComplete="given-name"
                autoFocus
                fullWidth
                error={!!errors.firstname}
                helperText={errors.firstname?.message}
              />
            </Grid>
            <Grid size={6}>
              <TextField
                {...register('lastname')}
                label="Last name"
                autoComplete="family-name"
                fullWidth
                error={!!errors.lastname}
                helperText={errors.lastname?.message}
              />
            </Grid>
          </Grid>

          <TextField
            {...register('email')}
            label="Email"
            type="email"
            autoComplete="email"
            fullWidth
            error={!!errors.email}
            helperText={errors.email?.message}
          />

          <TextField
            {...register('password')}
            label="Password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            fullWidth
            error={!!errors.password}
            helperText={errors.password?.message ?? 'Minimum 8 characters'}
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

          <Button
            type="submit"
            variant="contained"
            size="large"
            fullWidth
            disabled={isSubmitting}
            sx={{ mt: 1 }}
          >
            {isSubmitting ? <CircularProgress size={22} color="inherit" /> : 'Create account'}
          </Button>
        </Box>

        <Typography variant="body2" color="text.secondary" sx={{ mt: 3, textAlign: 'center' }}>
          Already have an account?{' '}
          <Link to={ROUTES.LOGIN} style={{ color: 'var(--accent-primary)', textDecoration: 'none' }}>
            Sign in
          </Link>
        </Typography>
      </motion.div>
    </Box>
  );
}
