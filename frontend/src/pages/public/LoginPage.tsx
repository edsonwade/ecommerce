import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { motion } from 'framer-motion';
import {
  Box,
  Button,
  CircularProgress,
  TextField,
  Typography,
  Alert,
  IconButton,
  InputAdornment,
} from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { authApi } from '@api/auth.api';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { ROUTES } from '@utils/constants';
import { normalizeError } from '@api/client';

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const navState = location.state as { from?: string; email?: string } | null;
  const from = navState?.from ?? ROUTES.ACCOUNT;
  const prefilledEmail = navState?.email ?? '';
  const setAuth = useAuthStore((s) => s.setAuth);
  const addToast = useUIStore((s) => s.addToast);
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: prefilledEmail, password: '' },
  });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      const res = await authApi.login(values);
      setAuth({
        accessToken: res.accessToken,
        refreshToken: res.refreshToken,
        userId: res.userId,
        email: res.email,
        role: res.role,
        tenantId: res.tenantId,
      });
      addToast({ message: `Welcome back, ${res.email}`, variant: 'success' });
      const defaultDest =
        res.role === 'ADMIN' ? ROUTES.ADMIN :
        res.role === 'SELLER' ? ROUTES.SELLER :
        ROUTES.ACCOUNT;
      const dest = navState?.from ?? defaultDest;
      navigate(dest, { replace: true });
    } catch (err) {
      const normalized = normalizeError(err);
      setServerError(
        normalized.status === 401 ? 'Invalid email or password' : normalized.message
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
        {/* Logo / Brand */}
        <Typography
          variant="h3"
          component="div"
          sx={{ mb: 1, color: 'primary.main', fontFamily: 'var(--font-serif)' }}
        >
          Obsidian Market
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
          Sign in to your account
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

          <TextField
            {...register('email')}
            label="Email"
            type="email"
            autoComplete="email"
            autoFocus
            fullWidth
            error={!!errors.email}
            helperText={errors.email?.message}
          />

          <TextField
            {...register('password')}
            label="Password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            fullWidth
            error={!!errors.password}
            helperText={errors.password?.message}
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
            {isSubmitting ? <CircularProgress size={22} color="inherit" /> : 'Sign in'}
          </Button>
        </Box>

        <Typography variant="body2" color="text.secondary" sx={{ mt: 3, textAlign: 'center' }}>
          No account?{' '}
          <Link
            to={ROUTES.REGISTER}
            style={{ color: 'var(--accent-primary)', textDecoration: 'none' }}
          >
            Create one
          </Link>
        </Typography>
      </motion.div>
    </Box>
  );
}
