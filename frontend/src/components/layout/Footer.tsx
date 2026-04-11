import { Box, Divider, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { ROUTES } from '@utils/constants';

export default function Footer() {
  return (
    <Box
      component="footer"
      sx={{
        mt: 'auto',
        borderTop: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
        px: { xs: 2, md: 6 },
        py: 4,
      }}
    >
      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 4,
          justifyContent: 'space-between',
          mb: 3,
        }}
      >
        <Box>
          <Typography
            variant="h6"
            sx={{ fontFamily: 'var(--font-serif)', color: 'primary.main', mb: 1 }}
          >
            Obsidian Market
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 260 }}>
            A high-contrast, editorial commerce experience for discerning buyers and sellers.
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <Box>
            <Typography
              variant="caption"
              sx={{ color: 'text.secondary', display: 'block', mb: 1.5 }}
            >
              SHOP
            </Typography>
            {[
              { label: 'Catalog', to: ROUTES.CATALOG },
            ].map(({ label, to }) => (
              <Typography
                key={label}
                component={Link}
                to={to}
                variant="body2"
                sx={{
                  display: 'block',
                  color: 'text.secondary',
                  textDecoration: 'none',
                  mb: 0.75,
                  '&:hover': { color: 'text.primary' },
                }}
              >
                {label}
              </Typography>
            ))}
          </Box>

          <Box>
            <Typography
              variant="caption"
              sx={{ color: 'text.secondary', display: 'block', mb: 1.5 }}
            >
              ACCOUNT
            </Typography>
            {[
              { label: 'Sign in', to: ROUTES.LOGIN },
              { label: 'Register', to: ROUTES.REGISTER },
            ].map(({ label, to }) => (
              <Typography
                key={label}
                component={Link}
                to={to}
                variant="body2"
                sx={{
                  display: 'block',
                  color: 'text.secondary',
                  textDecoration: 'none',
                  mb: 0.75,
                  '&:hover': { color: 'text.primary' },
                }}
              >
                {label}
              </Typography>
            ))}
          </Box>
        </Box>
      </Box>

      <Divider />

      <Box
        sx={{
          pt: 2.5,
          display: 'flex',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 1,
        }}
      >
        <Typography variant="caption" color="text.tertiary" sx={{ color: 'text.disabled' }}>
          © {new Date().getFullYear()} Obsidian Market. All rights reserved.
        </Typography>
        <Typography
          variant="caption"
          sx={{ color: 'text.disabled', fontFamily: 'var(--font-mono)' }}
        >
          SaaS Multi-Tenant Platform
        </Typography>
      </Box>
    </Box>
  );
}
