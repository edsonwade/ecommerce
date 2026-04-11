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

export default function PublicLayout() {
  const [cartOpen, setCartOpen] = useState(false);
  const { isAuthenticated, userId } = useAuthStore();

  const { data: cart } = useQuery({
    queryKey: [QUERY_KEYS.CART, userId],
    queryFn: () => cartApi.get(userId!),
    enabled: isAuthenticated && !!userId,
    staleTime: 0,
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
