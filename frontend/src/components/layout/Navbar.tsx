import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  AppBar,
  Avatar,
  Badge,
  Box,
  Button,
  IconButton,
  Menu,
  MenuItem,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  ShoppingBag,
  AccountCircle,
  Logout,
  Dashboard,
  Storefront,
  AdminPanelSettings,
  LightMode,
  DarkMode,
} from '@mui/icons-material';
import { useAuthStore } from '@stores/auth.store';
import { useUIStore } from '@stores/ui.store';
import { ROUTES } from '@utils/constants';

interface NavbarProps {
  onCartOpen?: () => void;
  cartItemCount?: number;
}

export default function Navbar({ onCartOpen, cartItemCount = 0 }: NavbarProps) {
  const navigate = useNavigate();
  const { isAuthenticated, role, email, clearAuth } = useAuthStore();
  const { themeMode, toggleTheme, addToast } = useUIStore();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const handleLogout = async () => {
    clearAuth();
    addToast({ message: 'Signed out successfully', variant: 'info' });
    navigate(ROUTES.LOGIN);
    setAnchorEl(null);
  };

  const dashboardRoute =
    role === 'ADMIN' ? ROUTES.ADMIN : role === 'SELLER' ? ROUTES.SELLER : ROUTES.ACCOUNT;

  return (
    <AppBar
      position="sticky"
      elevation={0}
      sx={{
        bgcolor: 'background.paper',
        borderBottom: '1px solid',
        borderColor: 'divider',
        backdropFilter: 'blur(12px)',
      }}
    >
      <Toolbar sx={{ gap: 2, px: { xs: 2, md: 4 } }}>
        {/* Logo */}
        <Typography
          component={Link}
          to={ROUTES.HOME}
          variant="h5"
          sx={{
            fontFamily: 'var(--font-serif)',
            color: 'primary.main',
            textDecoration: 'none',
            flexGrow: isMobile ? 1 : 0,
            mr: 4,
          }}
        >
          Obsidian Market
        </Typography>

        {/* Nav links — desktop */}
        {!isMobile && (
          <Box sx={{ display: 'flex', gap: 1, flexGrow: 1 }}>
            <Button
              component={Link}
              to={ROUTES.CATALOG}
              color="inherit"
              sx={{ color: 'text.secondary', '&:hover': { color: 'text.primary' } }}
            >
              Catalog
            </Button>
          </Box>
        )}

        {/* Actions */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {/* Theme toggle */}
          <Tooltip title={themeMode === 'dark' ? 'Light mode' : 'Dark mode'}>
            <IconButton onClick={toggleTheme} size="small" sx={{ color: 'text.secondary' }}>
              {themeMode === 'dark' ? <LightMode fontSize="small" /> : <DarkMode fontSize="small" />}
            </IconButton>
          </Tooltip>

          {/* Cart */}
          <Tooltip title="Cart">
            <IconButton onClick={onCartOpen} sx={{ color: 'text.primary' }}>
              <Badge
                badgeContent={cartItemCount}
                color="primary"
                sx={{ '& .MuiBadge-badge': { fontFamily: 'var(--font-mono)', fontSize: '0.65rem' } }}
              >
                <ShoppingBag />
              </Badge>
            </IconButton>
          </Tooltip>

          {/* User menu */}
          {isAuthenticated ? (
            <>
              <Tooltip title="Account">
                <IconButton
                  onClick={(e) => setAnchorEl(e.currentTarget)}
                  sx={{ p: 0.5 }}
                >
                  <Avatar
                    sx={{
                      width: 34,
                      height: 34,
                      bgcolor: 'primary.main',
                      color: 'primary.contrastText',
                      fontSize: '0.875rem',
                      fontWeight: 700,
                    }}
                  >
                    {email?.[0]?.toUpperCase() ?? 'U'}
                  </Avatar>
                </IconButton>
              </Tooltip>

              <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={() => setAnchorEl(null)}
                slotProps={{ paper: { sx: { mt: 1, minWidth: 180 } } }}
                transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
              >
                <MenuItem
                  onClick={() => { navigate(dashboardRoute); setAnchorEl(null); }}
                  sx={{ gap: 1.5 }}
                >
                  {role === 'ADMIN' ? (
                    <AdminPanelSettings fontSize="small" />
                  ) : role === 'SELLER' ? (
                    <Storefront fontSize="small" />
                  ) : (
                    <Dashboard fontSize="small" />
                  )}
                  <Typography variant="body2">
                    {role === 'ADMIN' ? 'Admin' : role === 'SELLER' ? 'Seller Hub' : 'My Account'}
                  </Typography>
                </MenuItem>
                <MenuItem onClick={handleLogout} sx={{ gap: 1.5, color: 'error.main' }}>
                  <Logout fontSize="small" />
                  <Typography variant="body2">Sign out</Typography>
                </MenuItem>
              </Menu>
            </>
          ) : (
            <Button
              component={Link}
              to={ROUTES.LOGIN}
              variant="outlined"
              size="small"
              startIcon={<AccountCircle />}
            >
              Sign in
            </Button>
          )}
        </Box>
      </Toolbar>
    </AppBar>
  );
}
