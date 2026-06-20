import { describe, it, expect } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@test/mocks/server';
import { createAppTheme } from '@theme/mui-theme';
import OrderDetailPage from './OrderDetailPage';
import type { OrderLineResponse } from '@api/types';

const theme = createAppTheme('dark');

const LINES: OrderLineResponse[] = [
  { id: 10, orderId: 1, productId: 1, quantity: 3 },
];

function renderOrderDetail(orderId = '1') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[`/account/orders/${orderId}`]}>
          <Routes>
            <Route path="/account/orders/:id" element={<OrderDetailPage />} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('OrderDetailPage — readable line items', () => {
  it('enriches each order line with the product name + quantity', async () => {
    server.use(
      http.get('/api/v1/order-lines/:orderId', () => HttpResponse.json(LINES)),
      // default products/:id handler returns the "Obsidian Pen" fixture
    );

    renderOrderDetail();

    // Product name resolved from product-service (was previously only "#1")
    await waitFor(() => {
      expect(screen.getByText('Obsidian Pen')).toBeInTheDocument();
    });
    // Quantity still shown, and the id remains as a small secondary reference
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('#1')).toBeInTheDocument();
  });

  it('falls back to "Product #id" when the product lookup fails (e.g. deleted)', async () => {
    server.use(
      http.get('/api/v1/order-lines/:orderId', () => HttpResponse.json(LINES)),
      http.get('/api/v1/products/:id', () => new HttpResponse(null, { status: 404 })),
    );

    renderOrderDetail();

    await waitFor(() => {
      expect(screen.getByText('Product #1')).toBeInTheDocument();
    });
    // Page still renders the order rather than breaking
    expect(screen.getByText('3')).toBeInTheDocument();
  });
});
