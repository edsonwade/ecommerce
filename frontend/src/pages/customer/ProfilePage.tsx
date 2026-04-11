import { useEffect } from 'react';
import { Box, Button, CircularProgress, Container, TextField, Typography, Alert } from '@mui/material';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { customersApi } from '@api/customers.api';
import { QUERY_KEYS } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';

const schema = z.object({
  firstname: z.string().min(1, 'Required'),
  lastname: z.string().min(1, 'Required'),
  email: z.string().email('Invalid email'),
  street: z.string().min(1, 'Required'),
  houseNumber: z.string().min(1, 'Required'),
  city: z.string().min(1, 'Required'),
  zipCode: z.string().min(1, 'Required'),
  country: z.string().min(1, 'Required'),
});
type FormValues = z.infer<typeof schema>;

export default function ProfilePage() {
  const { userId } = useAuthStore();
  const addToast = useUIStore((s) => s.addToast);
  const queryClient = useQueryClient();

  const { data: customer, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.CUSTOMER, userId],
    queryFn: () => customersApi.getById(userId!),
    enabled: !!userId,
  });

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    if (customer) {
      reset({
        firstname: customer.firstname,
        lastname: customer.lastname,
        email: customer.email,
        street: customer.address.street,
        houseNumber: customer.address.houseNumber,
        city: customer.address.city,
        zipCode: customer.address.zipCode,
        country: customer.address.country,
      });
    }
  }, [customer, reset]);

  const { mutateAsync: updateCustomer } = useMutation({
    mutationFn: (values: FormValues) =>
      customersApi.update(userId!, {
        firstname: values.firstname,
        lastname: values.lastname,
        email: values.email,
        address: {
          street: values.street,
          houseNumber: values.houseNumber,
          city: values.city,
          zipCode: values.zipCode,
          country: values.country,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.CUSTOMER, userId] });
      addToast({ message: 'Profile updated', variant: 'success' });
    },
    onError: () => {
      addToast({ message: 'Failed to update profile', variant: 'error' });
    },
  });

  if (isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}><CircularProgress /></Box>;
  }

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 5 }}>
        Profile
      </Typography>

      <Box component="form" onSubmit={handleSubmit((v) => updateCustomer(v))} sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <TextField {...register('firstname')} label="First name" error={!!errors.firstname} helperText={errors.firstname?.message} fullWidth />
          <TextField {...register('lastname')} label="Last name" error={!!errors.lastname} helperText={errors.lastname?.message} fullWidth />
        </Box>
        <TextField {...register('email')} label="Email" type="email" error={!!errors.email} helperText={errors.email?.message} fullWidth />

        <Typography variant="caption" sx={{ color: 'text.secondary', mt: 1 }}>
          DELIVERY ADDRESS
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 100px', gap: 2 }}>
          <TextField {...register('street')} label="Street" error={!!errors.street} helperText={errors.street?.message} fullWidth />
          <TextField {...register('houseNumber')} label="No." error={!!errors.houseNumber} helperText={errors.houseNumber?.message} fullWidth />
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 2 }}>
          <TextField {...register('city')} label="City" error={!!errors.city} helperText={errors.city?.message} fullWidth />
          <TextField {...register('zipCode')} label="ZIP" error={!!errors.zipCode} helperText={errors.zipCode?.message} fullWidth />
          <TextField {...register('country')} label="Country" error={!!errors.country} helperText={errors.country?.message} fullWidth />
        </Box>

        <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
          {isSubmitting ? <CircularProgress size={22} color="inherit" /> : 'Save changes'}
        </Button>
      </Box>
    </Container>
  );
}
