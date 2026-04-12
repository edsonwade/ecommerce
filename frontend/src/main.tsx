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
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
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
