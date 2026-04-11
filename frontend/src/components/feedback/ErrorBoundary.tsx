import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { ErrorOutlined } from '@mui/icons-material';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary]', error, info);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;

      return (
        <Box
          sx={{
            minHeight: '100vh',
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
              An unexpected error occurred. The error has been logged. Please try returning to the
              home page.
            </Typography>
          </Box>
          {this.state.error && (
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
              {this.state.error.message}
            </Typography>
          )}
          <Button variant="contained" onClick={this.handleReset}>
            Return to home
          </Button>
        </Box>
      );
    }

    return this.props.children;
  }
}
