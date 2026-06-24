import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Box } from '@mui/material';
import Navbar from '@components/layout/Navbar';
import Footer from '@components/layout/Footer';
import MobileNav from '@components/layout/MobileNav';
import CartDrawer from '@components/cart/CartDrawer';
import { useAuthStore } from '@stores/auth.store';
import { useQuery } from '@tanstack/react-query';
import { cartApi } from '@api/cart.api';
import { QUERY_KEYS } from '@utils/constants';

export default function CustomerLayout() {
  const [cartOpen, setCartOpen] = useState(false);
  const { userId, role } = useAuthStore();

  // Carts belong to buyers (USER). A SELLER/ADMIN visiting /account must not
  // trigger a cart fetch they have no cart for.
  const { data: cart } = useQuery({
    queryKey: [QUERY_KEYS.CART, userId],
    queryFn: () => cartApi.get(userId!).catch((err) => {
      if (err?.response?.status === 404 || err?.response?.status === 503) return null;
      throw err;
    }),
    enabled: !!userId && role === 'USER',
    staleTime: 30 * 1000,
    retry: false,
  });

  const cartItemCount = cart?.itemCount ?? 0;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <Navbar onCartOpen={() => setCartOpen(true)} cartItemCount={cartItemCount} />
      <Box component="main" sx={{ flexGrow: 1, pb: { xs: '60px', md: 0 } }}>
        <Outlet />
      </Box>
      <Footer />
      <MobileNav onCartOpen={() => setCartOpen(true)} cartItemCount={cartItemCount} />
      <CartDrawer open={cartOpen} onClose={() => setCartOpen(false)} />
    </Box>
  );
}
