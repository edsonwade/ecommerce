import { Link, useLocation } from 'react-router-dom';
import {
  BottomNavigation,
  BottomNavigationAction,
  Paper,
  Badge,
} from '@mui/material';
import { Home, Explore, ShoppingBag, Person } from '@mui/icons-material';
import { ROUTES } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';

interface MobileNavProps {
  onCartOpen?: () => void;
  cartItemCount?: number;
}

export default function MobileNav({ onCartOpen, cartItemCount = 0 }: MobileNavProps) {
  const location = useLocation();
  const { isAuthenticated } = useAuthStore();

  const getValue = () => {
    if (location.pathname === ROUTES.HOME) return 0;
    if (location.pathname.startsWith('/catalog')) return 1;
    if (location.pathname.startsWith('/account')) return 3;
    return false;
  };

  return (
    <Paper
      sx={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        display: { xs: 'block', md: 'none' },
        zIndex: 1200,
        borderTop: '1px solid',
        borderColor: 'divider',
        borderRadius: 0,
      }}
      elevation={0}
    >
      <BottomNavigation value={getValue()} sx={{ bgcolor: 'background.paper', height: 60 }}>
        <BottomNavigationAction
          component={Link}
          to={ROUTES.HOME}
          label="Home"
          icon={<Home />}
          sx={{ color: 'text.secondary', '&.Mui-selected': { color: 'primary.main' } }}
        />
        <BottomNavigationAction
          component={Link}
          to={ROUTES.CATALOG}
          label="Catalog"
          icon={<Explore />}
          sx={{ color: 'text.secondary', '&.Mui-selected': { color: 'primary.main' } }}
        />
        <BottomNavigationAction
          label="Cart"
          icon={
            <Badge badgeContent={cartItemCount} color="primary">
              <ShoppingBag />
            </Badge>
          }
          onClick={onCartOpen}
          sx={{ color: 'text.secondary' }}
        />
        <BottomNavigationAction
          component={Link}
          to={isAuthenticated ? ROUTES.ACCOUNT : ROUTES.LOGIN}
          label="Account"
          icon={<Person />}
          sx={{ color: 'text.secondary', '&.Mui-selected': { color: 'primary.main' } }}
        />
      </BottomNavigation>
    </Paper>
  );
}
