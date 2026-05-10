import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@test/test-utils';
import { server } from '@test/mocks/server';
import { http, HttpResponse } from 'msw';
import { useUIStore } from '@stores/ui.store';
import { useAuthStore } from '@stores/auth.store';
import ProductGrid from './ProductGrid';
import type { ProductResponse } from '@api/types';

const PRODUCT: ProductResponse = {
  id: 1,
  name: 'Obsidian Pen',
  description: 'A premium writing instrument',
  availableQuantity: 50,
  price: 29.99,
  categoryId: 1,
  categoryName: 'Stationery',
  categoryDescription: 'Writing essentials',
};

const CART_RESPONSE = {
  cartId: 'cart-001',
  customerId: 'user-001',
  items: [],
  total: 0,
  itemCount: 0,
  createdAt: '',
  updatedAt: '',
};

describe('ProductGrid — add to cart retry', () => {
  beforeEach(() => {
    useAuthStore.setState({
      isAuthenticated: true,
      userId: 'user-001',
      accessToken: 'mock-access-token',
      refreshToken: null,
      email: 'test@example.com',
      role: 'USER',
      tenantId: null,
    });
    useUIStore.setState({ toastQueue: [] });
  });

  afterEach(() => {
    useAuthStore.getState().clearAuth();
  });

  it('shows success toast after successful add', async () => {
    render(<ProductGrid products={[PRODUCT]} />);
    fireEvent.click(screen.getByText('Add to cart'));
    await waitFor(
      () => {
        const toasts = useUIStore.getState().toastQueue;
        expect(toasts.some((t) => t.message === 'Item added to cart' && t.variant === 'success')).toBe(true);
      },
      { timeout: 3000 }
    );
  });

  it('shows retrying toast then success when first call returns 503', async () => {
    let callCount = 0;
    server.use(
      http.post('/api/v1/carts/:customerId/items', () => {
        callCount++;
        if (callCount === 1) {
          return HttpResponse.json(
            { status: 503, message: 'Service unavailable — please try again later' },
            { status: 503 }
          );
        }
        return HttpResponse.json(CART_RESPONSE);
      })
    );

    render(<ProductGrid products={[PRODUCT]} />);
    fireEvent.click(screen.getByText('Add to cart'));

    await waitFor(
      () => {
        const toasts = useUIStore.getState().toastQueue;
        expect(toasts.some((t) => t.message.includes('retrying') && t.variant === 'warning')).toBe(true);
      },
      { timeout: 5000 }
    );

    await waitFor(
      () => {
        const toasts = useUIStore.getState().toastQueue;
        expect(toasts.some((t) => t.message === 'Item added to cart' && t.variant === 'success')).toBe(true);
      },
      { timeout: 5000 }
    );
  });

  it('shows 503-specific error when all attempts return 503', async () => {
    server.use(
      http.post('/api/v1/carts/:customerId/items', () =>
        HttpResponse.json(
          { status: 503, message: 'Service unavailable — please try again later' },
          { status: 503 }
        )
      )
    );

    render(<ProductGrid products={[PRODUCT]} />);
    fireEvent.click(screen.getByText('Add to cart'));

    await waitFor(
      () => {
        const toasts = useUIStore.getState().toastQueue;
        expect(
          toasts.some(
            (t) =>
              t.message === 'Cart service is temporarily unavailable. Please try again.' &&
              t.variant === 'error'
          )
        ).toBe(true);
      },
      { timeout: 8000 }
    );
  });

  it('shows generic error for non-503 failures', async () => {
    server.use(
      http.post('/api/v1/carts/:customerId/items', () =>
        HttpResponse.json({ message: 'Internal server error' }, { status: 500 })
      )
    );

    render(<ProductGrid products={[PRODUCT]} />);
    fireEvent.click(screen.getByText('Add to cart'));

    await waitFor(
      () => {
        const toasts = useUIStore.getState().toastQueue;
        expect(toasts.some((t) => t.message === 'Failed to add item to cart' && t.variant === 'error')).toBe(true);
      },
      { timeout: 3000 }
    );
  });

  it('renders empty state when no products provided', () => {
    render(<ProductGrid products={[]} />);
    expect(screen.getByText('No products found')).toBeInTheDocument();
  });

  it('renders product cards for each product', () => {
    render(<ProductGrid products={[PRODUCT, { ...PRODUCT, id: 2, name: 'Ember Notebook' }]} />);
    expect(screen.getByText('Obsidian Pen')).toBeInTheDocument();
    expect(screen.getByText('Ember Notebook')).toBeInTheDocument();
  });
});
