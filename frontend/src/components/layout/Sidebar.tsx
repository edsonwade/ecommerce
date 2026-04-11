import { Link, useLocation } from 'react-router-dom';
import {
  Box,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import type { SvgIconComponent } from '@mui/icons-material';

export interface NavItem {
  label: string;
  to: string;
  Icon: SvgIconComponent;
}

interface SidebarProps {
  items: NavItem[];
  title: string;
  open: boolean;
  onClose: () => void;
}

const DRAWER_WIDTH = 240;

export default function Sidebar({ items, title, open, onClose }: SidebarProps) {
  const location = useLocation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('lg'));

  const drawerContent = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Toolbar sx={{ px: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
        <Typography
          variant="caption"
          sx={{ color: 'text.secondary', letterSpacing: '0.12em' }}
        >
          {title.toUpperCase()}
        </Typography>
      </Toolbar>

      <List sx={{ px: 1, py: 1.5, flexGrow: 1 }}>
        {items.map(({ label, to, Icon }) => {
          const active = location.pathname === to || location.pathname.startsWith(to + '/');
          return (
            <ListItem key={to} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                component={Link}
                to={to}
                onClick={isMobile ? onClose : undefined}
                selected={active}
                sx={{
                  borderRadius: 1,
                  '&.Mui-selected': {
                    bgcolor: 'action.selected',
                    borderLeft: '3px solid',
                    borderColor: 'primary.main',
                    '& .MuiListItemIcon-root': { color: 'primary.main' },
                    '& .MuiListItemText-primary': { color: 'text.primary', fontWeight: 600 },
                  },
                  '&:hover': { bgcolor: 'action.hover' },
                }}
              >
                <ListItemIcon sx={{ minWidth: 36, color: 'text.secondary' }}>
                  <Icon fontSize="small" />
                </ListItemIcon>
                <ListItemText
                  primary={label}
                  slotProps={{ primary: { variant: 'body2', color: 'text.secondary' } as object }}
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>
    </Box>
  );

  return (
    <>
      {/* Mobile: temporary overlay drawer */}
      <Drawer
        variant="temporary"
        open={open && isMobile}
        onClose={onClose}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: 'block', lg: 'none' },
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, bgcolor: 'background.paper' },
        }}
      >
        {drawerContent}
      </Drawer>

      {/* Desktop: permanent drawer */}
      <Drawer
        variant="permanent"
        open={open}
        sx={{
          display: { xs: 'none', lg: 'block' },
          width: open ? DRAWER_WIDTH : 0,
          flexShrink: 0,
          transition: 'width 200ms ease',
          '& .MuiDrawer-paper': {
            width: DRAWER_WIDTH,
            bgcolor: 'background.paper',
            borderRight: '1px solid',
            borderColor: 'divider',
            overflowX: 'hidden',
            transition: 'width 200ms ease',
            position: 'relative',
            height: '100%',
          },
        }}
      >
        {drawerContent}
      </Drawer>
    </>
  );
}
