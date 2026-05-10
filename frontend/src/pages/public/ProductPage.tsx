import { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Divider,
  TextField,
  Typography,
  Alert,
} from '@mui/material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowBack, ShoppingBag } from '@mui/icons-material';
import { motion } from 'framer-motion';
import { productsApi } from '@api/products.api';
import { cartApi } from '@api/cart.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { formatCurrency } from '@utils/format';
import type { AppError } from '@api/types';

export default function ProductPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { isAuthenticated, userId } = useAuthStore();
  const addToast = useUIStore((s) => s.addToast);
  const [quantity, setQuantity] = useState(1);

  const { data: product, isLoading, isError } = useQuery({
    queryKey: [QUERY_KEYS.PRODUCT, id],
    queryFn: () => productsApi.getById(Number(id)),
    enabled: !!id,
    staleTime: 5 * 60 * 1000,
  });

  const prevFailureCount = useRef(0);

  const { mutate: addToCart, isPending, failureCount } = useMutation({
    mutationFn: () =>
      cartApi.addItem(userId!, {
        productId: product!.id,
        productName: product!.name,
        productDescription: product!.description,
        unitPrice: product!.price,
        quantity,
        availableQuantity: product!.availableQuantity,
      }),
    retry: (count, error) => count <= 1 && (error as unknown as AppError).status === 503,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.CART, userId] });
      addToast({ message: `${product?.name} added to cart`, variant: 'success' });
    },
    onError: (error) => {
      const is503 = (error as unknown as AppError).status === 503;
      addToast({
        message: is503
          ? 'Cart service is temporarily unavailable. Please try again.'
          : 'Failed to add to cart',
        variant: 'error',
      });
    },
  });

  useEffect(() => {
    if (failureCount === 1 && isPending && prevFailureCount.current === 0) {
      addToast({ message: 'Cart service is busy — retrying…', variant: 'warning' });
    }
    prevFailureCount.current = failureCount;
  }, [failureCount, isPending, addToast]);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (isError || !product) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <Alert severity="error">Product not found.</Alert>
      </Container>
    );
  }

  const outOfStock = product.availableQuantity === 0;

  return (
    <Container maxWidth="lg" sx={{ py: 6, px: { xs: 2, md: 4 } }}>
      <Button
        component={Link}
        to={ROUTES.CATALOG}
        startIcon={<ArrowBack />}
        sx={{ mb: 4, color: 'text.secondary' }}
      >
        Back to catalog
      </Button>

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
          gap: 6,
        }}
      >
        {/* Image */}
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.4, ease: [0.2, 0, 0, 1] }}
        >
          <Box
            sx={{
              aspectRatio: '4/3',
              bgcolor: 'var(--surface-sunken)',
              borderRadius: 2,
              border: '1px solid',
              borderColor: 'divider',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
            }}
          >
            <Typography
              sx={{
                fontFamily: 'var(--font-serif)',
                fontSize: '5rem',
                opacity: 0.1,
                color: 'text.primary',
              }}
            >
              {product.name.slice(0, 2).toUpperCase()}
            </Typography>
          </Box>
        </motion.div>

        {/* Details */}
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.4, delay: 0.1, ease: [0.2, 0, 0, 1] }}
        >
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            <Box>
              <Typography variant="caption" sx={{ color: 'primary.main', display: 'block', mb: 1 }}>
                {product.categoryName?.toUpperCase() ?? 'GENERAL'}
              </Typography>
              <Typography variant="h2" sx={{ fontFamily: 'var(--font-serif)', mb: 1 }}>
                {product.name}
              </Typography>
              <Typography
                variant="h4"
                sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}
              >
                {formatCurrency(product.price)}
              </Typography>
            </Box>

            <Divider />

            <Typography variant="body1" color="text.secondary" sx={{ lineHeight: 1.7 }}>
              {product.description}
            </Typography>

            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Chip
                label={outOfStock ? 'Out of stock' : `${product.availableQuantity} in stock`}
                size="small"
                sx={{
                  bgcolor: outOfStock ? 'var(--status-error)' : 'var(--status-success)',
                  color: '#fff',
                  fontFamily: 'var(--font-mono)',
                  fontSize: '0.75rem',
                }}
              />
            </Box>

            {!outOfStock && (
              <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
                <TextField
                  type="number"
                  label="Quantity"
                  size="small"
                  value={quantity}
                  onChange={(e) =>
                    setQuantity(Math.max(1, Math.min(product.availableQuantity, Number(e.target.value))))
                  }
                  sx={{ width: 100 }}
                  slotProps={{ htmlInput: { min: 1, max: product.availableQuantity } }}
                />
                <Button
                  variant="contained"
                  size="large"
                  startIcon={isPending ? <CircularProgress size={16} color="inherit" /> : <ShoppingBag />}
                  disabled={!isAuthenticated || isPending}
                  onClick={() => addToCart()}
                  sx={{ flex: 1 }}
                >
                  {!isAuthenticated
                    ? 'Sign in to purchase'
                    : isPending && failureCount > 0
                    ? 'Retrying…'
                    : 'Add to cart'}
                </Button>
              </Box>
            )}
          </Box>
        </motion.div>
      </Box>
    </Container>
  );
}
