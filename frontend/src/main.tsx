/* eslint-disable react-refresh/only-export-components */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { useUIStore } from '@stores/ui.store';
import { createAppTheme } from '@theme/mui-theme';
import { router } from '@routes/index';
import ToastStack from '@components/feedback/Toast';
import ErrorBoundary from '@components/feedback/ErrorBoundary';
import './i18n/config';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Transient gateway blips (503 fallback, 502/504, or a dropped connection that
      // surfaces as status 0) should self-heal before the user ever sees an error —
      // the backends can be slow to warm up on the first call after login. Retry those
      // up to 4 times with a short backoff; everything else (404/403/401/400) retries
      // at most once, as before.
      retry: (failureCount, error) => {
        const status = (error as { status?: number } | null)?.status;
        if (status === 503 || status === 502 || status === 504 || status === 0) {
          return failureCount < 4;
        }
        return failureCount < 1;
      },
      retryDelay: (attempt) => Math.min(300 * 2 ** attempt, 2000),
      refetchOnWindowFocus: false,
    },
  },
});

function AppWithTheme() {
  const themeMode = useUIStore((s) => s.themeMode);
  const theme = createAppTheme(themeMode);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <div data-theme={themeMode}>
        <ErrorBoundary>
          <RouterProvider router={router} />
        </ErrorBoundary>
        <ToastStack />
      </div>
    </ThemeProvider>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AppWithTheme />
    </QueryClientProvider>
  </StrictMode>
);
