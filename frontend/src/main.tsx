/* eslint-disable react-refresh/only-export-components */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { shouldRetryQuery, queryRetryDelay } from './api/queryRetry';
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
      // surfaces as status 0) self-heal with a couple of quick retries; everything else
      // (404/403/401/400) is deterministic and retries at most once. Bounded on purpose —
      // see queryRetry.ts (the old 4×/2s policy stacked into multi-second stalls on a
      // flaky host↔container hop).
      retry: shouldRetryQuery,
      retryDelay: queryRetryDelay,
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
