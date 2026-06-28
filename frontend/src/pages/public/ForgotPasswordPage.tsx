import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { motion } from 'framer-motion';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  TextField,
  Typography,
} from '@mui/material';
import { authApi } from '@api/auth.api';
import { ROUTES } from '@utils/constants';
import { normalizeError } from '@api/client';

const schema = z.object({
  email: z.string().email('Enter a valid email'),
});

type FormValues = z.infer<typeof schema>;

export default function ForgotPasswordPage() {
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '' },
  });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      await authApi.forgotPassword(values.email);
      // The backend returns the same response whether or not the account exists, so we
      // always show the neutral confirmation — never reveal whether an email is registered.
      setSubmitted(true);
    } catch (err) {
      setServerError(normalizeError(err).message);
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
          Reset your password
        </Typography>

        {submitted ? (
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
            <Alert severity="success" sx={{ borderRadius: 1 }}>
              If an account exists for that email, a reset link has been sent. Please check your
              inbox.
            </Alert>
            <Typography variant="body2" color="text.secondary">
              The link expires in 30 minutes and can be used once.
            </Typography>
            <Button component={Link} to={ROUTES.LOGIN} variant="contained" size="large" fullWidth>
              Back to sign in
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

            <Typography variant="body2" color="text.secondary">
              Enter the email associated with your account and we&apos;ll send you a link to reset
              your password.
            </Typography>

            <TextField
              {...register('email', { onChange: () => setServerError(null) })}
              label="Email"
              type="email"
              autoComplete="email"
              autoFocus
              fullWidth
              error={!!errors.email}
              helperText={errors.email?.message}
            />

            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={isSubmitting}
              sx={{ mt: 1 }}
            >
              {isSubmitting ? <CircularProgress size={22} color="inherit" /> : 'Send reset link'}
            </Button>
          </Box>
        )}

        <Typography variant="body2" color="text.secondary" sx={{ mt: 3, textAlign: 'center' }}>
          Remembered it?{' '}
          <Link to={ROUTES.LOGIN} style={{ color: 'var(--accent-primary)', textDecoration: 'none' }}>
            Sign in
          </Link>
        </Typography>
      </motion.div>
    </Box>
  );
}
