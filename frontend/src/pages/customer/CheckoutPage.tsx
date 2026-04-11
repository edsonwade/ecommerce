import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Container,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from '@mui/material';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import { cartApi } from '@api/cart.api';
import { ordersApi } from '@api/orders.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { formatCurrency } from '@utils/format';
import OrderTimeline from '@components/order/OrderTimeline';
import type { OrderStatus, PaymentMethod } from '@api/types';

const addressSchema = z.object({
  firstname: z.string().min(1, 'Required'),
  lastname: z.string().min(1, 'Required'),
  street: z.string().min(1, 'Required'),
  houseNumber: z.string().min(1, 'Required'),
  city: z.string().min(1, 'Required'),
  zipCode: z.string().min(1, 'Required'),
  country: z.string().min(1, 'Required'),
});

type AddressForm = z.infer<typeof addressSchema>;

const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'CREDIT_CARD', label: 'Credit Card' },
  { value: 'VISA', label: 'Visa' },
  { value: 'MASTER_CARD', label: 'Mastercard' },
  { value: 'PAYPAL', label: 'PayPal' },
  { value: 'BITCOIN', label: 'Bitcoin' },
];

const STEPS = ['Delivery address', 'Payment method', 'Confirm order'];

export default function CheckoutPage() {
  const navigate = useNavigate();
  const { userId } = useAuthStore();
  const addToast = useUIStore((s) => s.addToast);
  const [activeStep, setActiveStep] = useState(0);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CREDIT_CARD');
  const [correlationId, setCorrelationId] = useState<string | null>(null);

  const { data: cart, isLoading: cartLoading } = useQuery({
    queryKey: [QUERY_KEYS.CART, userId],
    queryFn: () => cartApi.checkout(userId!),
    enabled: !!userId,
  });

  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors },
  } = useForm<AddressForm>({ resolver: zodResolver(addressSchema) });

  const { mutate: placeOrder, isPending: placingOrder, error: orderError } = useMutation({
    mutationFn: () =>
      ordersApi.create({
        amount: cart!.total,
        paymentMethod,
        customerId: userId!,
        products: cart!.items.map((i) => ({
          productId: i.productId,
          quantity: i.quantity,
        })),
      }),
    onSuccess: (res) => {
      setCorrelationId(res.correlationId);
      setActiveStep(3);
    },
    onError: () => {
      addToast({ message: 'Failed to place order. Please try again.', variant: 'error' });
    },
  });

  const handleTimelineComplete = (status: OrderStatus) => {
    if (status === 'CONFIRMED') {
      cartApi.clear(userId!);
      addToast({ message: 'Order confirmed!', variant: 'success' });
    } else if (status === 'CANCELLED') {
      addToast({ message: 'Order was cancelled. Please try again.', variant: 'error' });
    }
  };

  if (cartLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!cart || cart.items.length === 0) {
    return (
      <Container maxWidth="sm" sx={{ py: 8, textAlign: 'center' }}>
        <Alert severity="info">Your cart is empty. Add items before checking out.</Alert>
        <Button variant="contained" sx={{ mt: 3 }} onClick={() => navigate(ROUTES.CATALOG)}>
          Browse catalog
        </Button>
      </Container>
    );
  }

  return (
    <Container maxWidth="md" sx={{ py: 6, px: { xs: 2, md: 4 } }}>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 5 }}>
        Checkout
      </Typography>

      {activeStep < 3 && (
        <Stepper activeStep={activeStep} sx={{ mb: 5 }}>
          {STEPS.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
      )}

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 380px' }, gap: 4 }}>
        {/* Main content */}
        <Box>
          <AnimatePresence mode="wait">
            {/* Step 0: Address */}
            {activeStep === 0 && (
              <motion.div
                key="address"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.25 }}
              >
                <Box
                  component="form"
                  onSubmit={handleSubmit(() => setActiveStep(1))}
                  sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}
                >
                  <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
                    <TextField {...register('firstname')} label="First name" error={!!errors.firstname} helperText={errors.firstname?.message} fullWidth />
                    <TextField {...register('lastname')} label="Last name" error={!!errors.lastname} helperText={errors.lastname?.message} fullWidth />
                  </Box>
                  <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 100px', gap: 2 }}>
                    <TextField {...register('street')} label="Street" error={!!errors.street} helperText={errors.street?.message} fullWidth />
                    <TextField {...register('houseNumber')} label="No." error={!!errors.houseNumber} helperText={errors.houseNumber?.message} fullWidth />
                  </Box>
                  <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 2 }}>
                    <TextField {...register('city')} label="City" error={!!errors.city} helperText={errors.city?.message} fullWidth />
                    <TextField {...register('zipCode')} label="ZIP code" error={!!errors.zipCode} helperText={errors.zipCode?.message} fullWidth />
                    <TextField {...register('country')} label="Country" error={!!errors.country} helperText={errors.country?.message} fullWidth />
                  </Box>
                  <Button type="submit" variant="contained" size="large">
                    Continue to payment
                  </Button>
                </Box>
              </motion.div>
            )}

            {/* Step 1: Payment */}
            {activeStep === 1 && (
              <motion.div
                key="payment"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.25 }}
              >
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                  <FormControl fullWidth>
                    <InputLabel>Payment method</InputLabel>
                    <Select
                      value={paymentMethod}
                      label="Payment method"
                      onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
                    >
                      {PAYMENT_METHODS.map((m) => (
                        <MenuItem key={m.value} value={m.value}>
                          {m.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <Box sx={{ display: 'flex', gap: 2 }}>
                    <Button variant="outlined" onClick={() => setActiveStep(0)}>
                      Back
                    </Button>
                    <Button variant="contained" size="large" onClick={() => setActiveStep(2)}>
                      Review order
                    </Button>
                  </Box>
                </Box>
              </motion.div>
            )}

            {/* Step 2: Confirm */}
            {activeStep === 2 && (
              <motion.div
                key="confirm"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.25 }}
              >
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                  <Box>
                    <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
                      DELIVERY ADDRESS
                    </Typography>
                    <Typography variant="body2">
                      {getValues('firstname')} {getValues('lastname')}
                      <br />
                      {getValues('street')} {getValues('houseNumber')}, {getValues('zipCode')}{' '}
                      {getValues('city')}, {getValues('country')}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
                      PAYMENT
                    </Typography>
                    <Typography variant="body2">
                      {PAYMENT_METHODS.find((m) => m.value === paymentMethod)?.label}
                    </Typography>
                  </Box>

                  {orderError && (
                    <Alert severity="error">Failed to place order. Please try again.</Alert>
                  )}

                  <Box sx={{ display: 'flex', gap: 2 }}>
                    <Button variant="outlined" onClick={() => setActiveStep(1)} disabled={placingOrder}>
                      Back
                    </Button>
                    <Button
                      variant="contained"
                      size="large"
                      onClick={() => placeOrder()}
                      disabled={placingOrder}
                      startIcon={placingOrder ? <CircularProgress size={16} color="inherit" /> : null}
                    >
                      Place order — {formatCurrency(cart.total)}
                    </Button>
                  </Box>
                </Box>
              </motion.div>
            )}

            {/* Step 3: Live tracking */}
            {activeStep === 3 && correlationId && (
              <motion.div
                key="tracking"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.35 }}
              >
                <Typography variant="h4" sx={{ fontFamily: 'var(--font-serif)', mb: 1 }}>
                  Order tracking
                </Typography>
                <Typography
                  variant="caption"
                  sx={{ fontFamily: 'var(--font-mono)', color: 'text.secondary', display: 'block', mb: 3 }}
                >
                  Correlation ID: {correlationId}
                </Typography>
                <OrderTimeline correlationId={correlationId} onComplete={handleTimelineComplete} />
                <Button
                  variant="outlined"
                  sx={{ mt: 3 }}
                  onClick={() => navigate(ROUTES.ORDERS)}
                >
                  View my orders
                </Button>
              </motion.div>
            )}
          </AnimatePresence>
        </Box>

        {/* Order summary */}
        {activeStep < 3 && (
          <Box
            sx={{
              bgcolor: 'background.paper',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 2,
              p: 3,
              height: 'fit-content',
              position: 'sticky',
              top: 80,
            }}
          >
            <Typography variant="h6" sx={{ fontFamily: 'var(--font-serif)', mb: 2.5 }}>
              Order summary
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, mb: 2.5 }}>
              {cart.items.map((item) => (
                <Box key={item.productId} sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">
                    {item.productName} × {item.quantity}
                  </Typography>
                  <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)' }}>
                    {formatCurrency(item.lineTotal)}
                  </Typography>
                </Box>
              ))}
            </Box>
            <Divider />
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2 }}>
              <Typography variant="body1" sx={{ fontWeight: 600 }}>
                Total
              </Typography>
              <Typography
                variant="h6"
                sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}
              >
                {formatCurrency(cart.total)}
              </Typography>
            </Box>
          </Box>
        )}
      </Box>
    </Container>
  );
}
