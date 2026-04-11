import { Box, Button, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { ROUTES } from '@utils/constants';

export default function NotFoundPage() {
  return (
    <Box
      sx={{
        minHeight: '60vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        px: 3,
        gap: 3,
      }}
    >
      <Typography
        variant="h1"
        sx={{
          fontFamily: 'var(--font-mono)',
          fontSize: { xs: '6rem', md: '10rem' },
          color: 'primary.main',
          lineHeight: 1,
          opacity: 0.6,
        }}
      >
        404
      </Typography>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)' }}>
        Page not found
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 360 }}>
        The page you&apos;re looking for doesn&apos;t exist or has been moved.
      </Typography>
      <Button variant="contained" component={Link} to={ROUTES.HOME}>
        Return home
      </Button>
    </Box>
  );
}
