import { Outlet } from 'react-router-dom';
import { Box, useMediaQuery, useTheme } from '@mui/material';
import { MotionConfig } from 'framer-motion';
import Navbar from '@components/layout/Navbar';
import Sidebar from '@components/layout/Sidebar';
import { useUIStore } from '@stores/ui.store';
import { ROUTES } from '@utils/constants';
import {
  Dashboard,
  Business,
  People,
  Inventory2,
  Category,
  CreditCard,
  BarChart,
  ManageAccounts,
} from '@mui/icons-material';

const ADMIN_NAV = [
  { label: 'Dashboard', to: ROUTES.ADMIN, Icon: Dashboard },
  { label: 'Tenants', to: ROUTES.ADMIN_TENANTS, Icon: Business },
  { label: 'Users', to: ROUTES.ADMIN_USERS, Icon: People },
  { label: 'Products', to: ROUTES.ADMIN_PRODUCTS, Icon: Inventory2 },
  { label: 'Categories', to: ROUTES.ADMIN_CATEGORIES, Icon: Category },
  { label: 'Payments', to: ROUTES.ADMIN_PAYMENTS, Icon: CreditCard },
  { label: 'Analytics', to: ROUTES.ADMIN_ANALYTICS, Icon: BarChart },
  { label: 'Account', to: ROUTES.ADMIN_ACCOUNT, Icon: ManageAccounts },
];

export default function AdminLayout() {
  const { sidebarOpen, setSidebarOpen, toggleSidebar } = useUIStore();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  return (
    <MotionConfig reducedMotion="user">
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
        <Navbar onMenuClick={isMobile ? toggleSidebar : undefined} />
        <Box sx={{ display: 'flex', flexGrow: 1, overflow: 'hidden' }}>
          <Sidebar
            items={ADMIN_NAV}
            title="Admin"
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
    </MotionConfig>
  );
}
