import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Box, Button, Chip, Typography } from '@mui/material';
import { ShoppingBag, Visibility } from '@mui/icons-material';
import { motion } from 'framer-motion';
import type { ProductResponse } from '@api/types';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { ROUTES } from '@utils/constants';
import { formatCurrency } from '@utils/format';

interface ProductCardProps {
  product: ProductResponse;
  onAddToCart?: (product: ProductResponse) => void;
}

export default function ProductCard({ product, onAddToCart }: ProductCardProps) {
  const [hovered, setHovered] = useState(false);
  const { isAuthenticated } = useAuthStore();
  const addToast = useUIStore((s) => s.addToast);
  const outOfStock = product.availableQuantity === 0;

  const handleAddToCart = (e: React.MouseEvent) => {
    e.preventDefault();
    if (!isAuthenticated) {
      addToast({ message: 'Sign in to add items to your cart', variant: 'info' });
      return;
    }
    if (onAddToCart) onAddToCart(product);
  };

  return (
    <Box
      component={Link}
      to={ROUTES.PRODUCT(product.id)}
      sx={{ textDecoration: 'none', display: 'block' }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <Box
        sx={{
          border: '1px solid',
          borderColor: hovered ? 'var(--border-emphasis)' : 'var(--border-default)',
          borderRadius: 2,
          overflow: 'hidden',
          bgcolor: 'background.paper',
          transition: 'border-color 200ms cubic-bezier(0.2, 0, 0, 1)',
          boxShadow: hovered ? '0 2px 8px rgba(0,0,0,0.3)' : 'none',
        }}
      >
        {/* Image area */}
        <Box
          sx={{
            position: 'relative',
            aspectRatio: '4/3',
            bgcolor: 'var(--surface-sunken)',
            overflow: 'hidden',
          }}
        >
          {/* Placeholder gradient image */}
          <Box
            sx={{
              width: '100%',
              height: '100%',
              background: `linear-gradient(135deg, var(--surface-raised) 0%, var(--border-emphasis) 100%)`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transform: hovered ? 'scale(1.03)' : 'scale(1)',
              transition: 'transform 200ms ease-out',
              filter: outOfStock ? 'grayscale(1)' : 'none',
            }}
          >
            <Typography
              sx={{
                fontFamily: 'var(--font-serif)',
                fontSize: '2rem',
                opacity: 0.15,
                color: 'text.primary',
                textAlign: 'center',
                px: 2,
              }}
            >
              {product.name.slice(0, 2).toUpperCase()}
            </Typography>
          </Box>

          {/* Quick-view overlay */}
          {hovered && !outOfStock && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.15 }}
              style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'rgba(0,0,0,0.3)',
              }}
            >
              <Button
                variant="outlined"
                size="small"
                startIcon={<Visibility fontSize="small" />}
                sx={{
                  bgcolor: 'background.paper',
                  borderColor: 'divider',
                  '&:hover': { bgcolor: 'background.paper' },
                }}
              >
                Quick view
              </Button>
            </motion.div>
          )}

          {outOfStock && (
            <Box
              sx={{
                position: 'absolute',
                top: 8,
                left: 8,
              }}
            >
              <Chip
                label="Out of stock"
                size="small"
                sx={{
                  bgcolor: 'var(--status-error)',
                  color: '#fff',
                  fontSize: '0.65rem',
                  height: 20,
                }}
              />
            </Box>
          )}
        </Box>

        {/* Content */}
        <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 0.75 }}>
          <Typography
            variant="caption"
            sx={{ color: 'text.secondary', letterSpacing: '0.08em' }}
          >
            {product.categoryName?.toUpperCase() ?? 'GENERAL'}
          </Typography>

          <Typography
            variant="h6"
            sx={{
              fontFamily: 'var(--font-serif)',
              fontSize: '1rem',
              lineHeight: 1.3,
              color: 'text.primary',
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            {product.name}
          </Typography>

          <Typography
            variant="body1"
            sx={{
              fontFamily: 'var(--font-mono)',
              color: 'primary.main',
              fontWeight: 500,
              fontSize: '1rem',
            }}
          >
            {formatCurrency(product.price)}
          </Typography>

          <Button
            variant="contained"
            size="small"
            fullWidth
            startIcon={<ShoppingBag fontSize="small" />}
            disabled={outOfStock}
            onClick={handleAddToCart}
            sx={{ mt: 0.5 }}
          >
            {outOfStock ? 'Out of stock' : 'Add to cart'}
          </Button>
        </Box>
      </Box>
    </Box>
  );
}
