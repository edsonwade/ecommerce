import { Link } from 'react-router-dom';
import {
  Box,
  Button,
  Divider,
  Drawer,
  IconButton,
  Typography,
  CircularProgress,
} from '@mui/material';
import { Close, ShoppingBag } from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '@api/cart.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { formatCurrency } from '@utils/format';
import CartItem from './CartItem';
import type { CartResponse } from '@api/types';

interface CartDrawerProps {
  open: boolean;
  onClose: () => void;
}

export default function CartDrawer({ open, onClose }: CartDrawerProps) {
  const { isAuthenticated, userId } = useAuthStore();
  const addToast = useUIStore((s) => s.addToast);
  const queryClient = useQueryClient();

  const { data: cart, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.CART, userId],
    queryFn: () => cartApi.get(userId!),
    enabled: isAuthenticated && !!userId,
    staleTime: 0,
  });

  const { mutate: removeItem } = useMutation({
    mutationFn: (productId: number) => cartApi.removeItem(userId!, productId),
    onMutate: async (productId) => {
      await queryClient.cancelQueries({ queryKey: [QUERY_KEYS.CART, userId] });
      const snapshot = queryClient.getQueryData<CartResponse>([QUERY_KEYS.CART, userId]);
      queryClient.setQueryData<CartResponse>([QUERY_KEYS.CART, userId], (old) => {
        if (!old) return old;
        const items = old.items.filter((i) => i.productId !== productId);
        const total = items.reduce((sum, i) => sum + i.lineTotal, 0);
        return { ...old, items, total, itemCount: items.length };
      });
      return { snapshot };
    },
    onError: (_err, _pid, ctx) => {
      queryClient.setQueryData([QUERY_KEYS.CART, userId], ctx?.snapshot);
      addToast({ message: 'Failed to remove item', variant: 'error' });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.CART, userId] });
    },
  });

  const { mutate: updateQty } = useMutation({
    mutationFn: ({ productId, quantity }: { productId: number; quantity: number }) =>
      cartApi.updateQuantity(userId!, productId, quantity),
    onMutate: async ({ productId, quantity }) => {
      await queryClient.cancelQueries({ queryKey: [QUERY_KEYS.CART, userId] });
      const snapshot = queryClient.getQueryData<CartResponse>([QUERY_KEYS.CART, userId]);
      queryClient.setQueryData<CartResponse>([QUERY_KEYS.CART, userId], (old) => {
        if (!old) return old;
        const items = old.items.map((i) =>
          i.productId === productId
            ? { ...i, quantity, lineTotal: i.unitPrice * quantity }
            : i
        );
        const total = items.reduce((sum, i) => sum + i.lineTotal, 0);
        return { ...old, items, total };
      });
      return { snapshot };
    },
    onError: (_err, _vars, ctx) => {
      queryClient.setQueryData([QUERY_KEYS.CART, userId], ctx?.snapshot);
      addToast({ message: 'Failed to update quantity', variant: 'error' });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.CART, userId] });
    },
  });

  const itemCount = cart?.itemCount ?? 0;

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      slotProps={{
        paper: {
          sx: {
            width: { xs: '100vw', sm: 420 },
            bgcolor: 'background.paper',
            borderLeft: '1px solid',
            borderColor: 'divider',
            display: 'flex',
            flexDirection: 'column',
          },
        },
      }}
    >
      {/* Header */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          px: 3,
          py: 2.5,
          borderBottom: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <ShoppingBag sx={{ color: 'text.secondary' }} />
          <Typography variant="h6" sx={{ fontFamily: 'var(--font-serif)' }}>
            Cart
          </Typography>
          {itemCount > 0 && (
            <Typography
              variant="caption"
              sx={{
                fontFamily: 'var(--font-mono)',
                bgcolor: 'primary.main',
                color: 'primary.contrastText',
                borderRadius: '9999px',
                px: 1,
                py: 0.25,
                fontSize: '0.7rem',
              }}
            >
              {itemCount}
            </Typography>
          )}
        </Box>
        <IconButton onClick={onClose} size="small" aria-label="Close cart">
          <Close />
        </IconButton>
      </Box>

      {/* Content */}
      <Box sx={{ flexGrow: 1, overflowY: 'auto', px: 3, py: 2 }}>
        {!isAuthenticated ? (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <ShoppingBag sx={{ fontSize: 56, color: 'text.disabled', mb: 2 }} />
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
              Sign in to view your cart
            </Typography>
            <Button variant="contained" component={Link} to={ROUTES.LOGIN} onClick={onClose}>
              Sign in
            </Button>
          </Box>
        ) : isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress size={32} />
          </Box>
        ) : !cart || cart.items.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <ShoppingBag sx={{ fontSize: 56, color: 'text.disabled', mb: 2 }} />
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
              Your cart is empty
            </Typography>
            <Button variant="outlined" component={Link} to={ROUTES.CATALOG} onClick={onClose}>
              Browse catalog
            </Button>
          </Box>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {cart.items.map((item) => (
              <CartItem
                key={item.productId}
                item={item}
                onRemove={() => removeItem(item.productId)}
                onQuantityChange={(q) => updateQty({ productId: item.productId, quantity: q })}
              />
            ))}
          </Box>
        )}
      </Box>

      {/* Footer */}
      {cart && cart.items.length > 0 && isAuthenticated && (
        <Box
          sx={{
            borderTop: '1px solid',
            borderColor: 'divider',
            px: 3,
            py: 3,
            bgcolor: 'background.paper',
          }}
        >
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              mb: 3,
            }}
          >
            <Typography variant="body1" color="text.secondary">
              Subtotal
            </Typography>
            <Typography
              variant="h5"
              sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}
            >
              {formatCurrency(cart.total)}
            </Typography>
          </Box>
          <Divider sx={{ mb: 2 }} />
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            <Button
              variant="contained"
              size="large"
              fullWidth
              component={Link}
              to={ROUTES.CHECKOUT}
              onClick={onClose}
            >
              Proceed to checkout
            </Button>
            <Button variant="outlined" size="large" fullWidth onClick={onClose}>
              Continue shopping
            </Button>
          </Box>
        </Box>
      )}
    </Drawer>
  );
}
