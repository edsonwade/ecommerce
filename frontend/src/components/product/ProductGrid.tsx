import { Box } from '@mui/material';
import type { ProductResponse } from '@api/types';
import ProductCard from './ProductCard';
import EmptyState from '@components/feedback/EmptyState';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '@api/cart.api';
import { QUERY_KEYS } from '@utils/constants';

interface ProductGridProps {
  products: ProductResponse[];
}

export default function ProductGrid({ products }: ProductGridProps) {
  const { isAuthenticated, userId } = useAuthStore();
  const addToast = useUIStore((s) => s.addToast);
  const queryClient = useQueryClient();

  const { mutate: addToCart } = useMutation({
    mutationFn: (product: ProductResponse) =>
      cartApi.addItem(userId!, {
        productId: product.id,
        productName: product.name,
        productDescription: product.description,
        unitPrice: product.price,
        quantity: 1,
        availableQuantity: product.availableQuantity,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.CART, userId] });
      addToast({ message: 'Item added to cart', variant: 'success' });
    },
    onError: () => {
      addToast({ message: 'Failed to add item to cart', variant: 'error' });
    },
  });

  const handleAddToCart = (product: ProductResponse) => {
    if (!isAuthenticated || !userId) return;
    addToCart(product);
  };

  if (products.length === 0) {
    return (
      <EmptyState
        title="No products found"
        description="Try adjusting your filters or check back later."
      />
    );
  }

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: {
          xs: '1fr',
          sm: 'repeat(2, 1fr)',
          md: 'repeat(3, 1fr)',
          lg: 'repeat(4, 1fr)',
        },
        gap: 2.5,
      }}
    >
      {products.map((product) => (
        <ProductCard key={product.id} product={product} onAddToCart={handleAddToCart} />
      ))}
    </Box>
  );
}
