import { Outlet } from 'react-router-dom';
import { Box } from '@mui/material';
import Navbar from '@components/layout/Navbar';
import Sidebar from '@components/layout/Sidebar';
import { useUIStore } from '@stores/ui.store';
import { ROUTES } from '@utils/constants';
import {
  Dashboard,
  Inventory2,
  ListAlt,
  Store,
} from '@mui/icons-material';

const SELLER_NAV = [
  { label: 'Dashboard', to: ROUTES.SELLER, Icon: Dashboard },
  { label: 'Products', to: ROUTES.SELLER_PRODUCTS, Icon: Store },
  { label: 'Orders', to: ROUTES.SELLER_ORDERS, Icon: ListAlt },
  { label: 'Inventory', to: ROUTES.SELLER_INVENTORY, Icon: Inventory2 },
];

export default function SellerLayout() {
  const { sidebarOpen, setSidebarOpen } = useUIStore();

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <Navbar />
      <Box sx={{ display: 'flex', flexGrow: 1, overflow: 'hidden' }}>
        <Sidebar
          items={SELLER_NAV}
          title="Seller Hub"
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
        />
        <Box
          component="main"
          sx={{ flexGrow: 1, overflow: 'auto', p: { xs: 2, md: 4 } }}
        >
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
