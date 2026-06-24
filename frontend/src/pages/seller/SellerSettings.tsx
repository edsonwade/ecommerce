import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Button, CircularProgress, Container, TextField, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { authApi } from '@api/auth.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import type { SellerProfileRequest, SellerProfileResponse } from '@api/types';

/**
 * Seller Business Profile - the legal "sold by" identity shown on every order invoice.
 * Pre-filled from the seller's current profile; saved via PUT /api/v1/auth/sellers/me.
 */
export default function SellerSettings() {
  const userId = useAuthStore((s) => s.userId);

  const { data: profile, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.SELLER_PROFILE, userId],
    queryFn: () => authApi.getSeller(userId as string),
    enabled: !!userId,
    staleTime: 60 * 1000,
  });

  if (isLoading || !profile) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
        <CircularProgress />
      </Box>
    );
  }

  // Remount the form whenever a fresh profile loads so its initial state comes straight
  // from props - no setState-in-effect, no cascading renders.
  return <ProfileForm key={profile.id} profile={profile} />;
}

function ProfileForm({ profile }: { profile: SellerProfileResponse }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const EMPTY: SellerProfileRequest = {
    companyName: '', vatNumber: '', street: '', city: '', country: '', postalCode: '',
  };
  const [form, setForm] = useState<SellerProfileRequest>({
    companyName: profile.companyName ?? '',
    vatNumber: profile.vatNumber ?? '',
    street: profile.street ?? '',
    city: profile.city ?? '',
    country: profile.country ?? '',
    postalCode: profile.postalCode ?? '',
  });

  const mutation = useMutation({
    mutationFn: (data: SellerProfileRequest) => authApi.updateSellerProfile(data),
    onSuccess: () => {
      addToast({ variant: 'success', message: 'Business profile saved' });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.SELLER_PROFILE] });
      // BUG 3: clear the form and return to the dashboard instead of leaving the
      // just-saved values sitting in the inputs with the seller stranded on the page.
      setForm(EMPTY);
      navigate(ROUTES.SELLER);
    },
    onError: () => addToast({ variant: 'error', message: 'Could not save business profile' }),
  });

  const field = (key: keyof SellerProfileRequest) => ({
    value: form[key] ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value })),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    mutation.mutate(form);
  };

  return (
    <Container maxWidth="sm" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 1 }}>Business profile</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
          This identity appears as the "sold by" block on every invoice your customers see.
          {profile.fullName ? ` Signed in as ${profile.fullName}.` : ''}
        </Typography>

        <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          <TextField label="Company name" fullWidth {...field('companyName')} />
          <TextField label="VAT / IVA number" fullWidth {...field('vatNumber')} />
          <TextField label="Street" fullWidth {...field('street')} />
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2.5 }}>
            <TextField label="Postal code" fullWidth {...field('postalCode')} />
            <TextField label="City" fullWidth {...field('city')} />
          </Box>
          <TextField label="Country" fullWidth {...field('country')} />

          <Button
            type="submit"
            variant="contained"
            disabled={mutation.isPending}
            sx={{ alignSelf: 'flex-start', mt: 1 }}
          >
            {mutation.isPending ? 'Saving...' : 'Save profile'}
          </Button>
        </Box>
      </motion.div>
    </Container>
  );
}
