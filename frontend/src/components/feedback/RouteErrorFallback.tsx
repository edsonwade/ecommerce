import { Box, Button, Typography } from '@mui/material';
import { ErrorOutlined } from '@mui/icons-material';
import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router-dom';

/**
 * Route-level error screen for React Router data routers. Render errors thrown inside a
 * route are caught by the router BEFORE they reach the app-level <ErrorBoundary> around
 * <RouterProvider>, so without an errorElement the router shows its raw developer page
 * ("Unexpected Application Error!"). This component is wired as `errorElement` on every
 * route branch to keep that page out of the user/seller/admin sections.
 */
export default function RouteErrorFallback() {
  const error = useRouteError();
  const navigate = useNavigate();

  console.error('[RouteErrorFallback]', error);

  const message = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : error instanceof Error
      ? error.message
      : null;

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
        bgcolor: 'background.default',
      }}
    >
      <ErrorOutlined sx={{ fontSize: 64, color: 'error.main', opacity: 0.6 }} />
      <Box>
        <Typography variant="h4" sx={{ fontFamily: 'var(--font-serif)', mb: 1 }}>
          Something went wrong
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 400 }}>
          An unexpected error occurred while loading this page. The error has been logged.
        </Typography>
      </Box>
      {message && (
        <Typography
          variant="caption"
          sx={{
            fontFamily: 'var(--font-mono)',
            color: 'error.main',
            bgcolor: 'background.paper',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1,
            px: 2,
            py: 1,
            maxWidth: 500,
            wordBreak: 'break-all',
          }}
        >
          {message}
        </Typography>
      )}
      <Box sx={{ display: 'flex', gap: 2 }}>
        <Button variant="outlined" onClick={() => navigate(-1)}>
          Go back
        </Button>
        <Button variant="contained" onClick={() => navigate('/')}>
          Return to home
        </Button>
      </Box>
    </Box>
  );
}
