import { describe, it, expect, vi, beforeEach } from 'vitest';
import { server } from '@test/mocks/server';
import { http, HttpResponse } from 'msw';
import apiClient from './client';

// vi.hoisted ensures these are available inside vi.mock factory closures
const { authStoreState, mockAddToast } = vi.hoisted(() => ({
  authStoreState: { accessToken: null as string | null, tenantId: null as string | null },
  mockAddToast: vi.fn().mockReturnValue('toast-id'),
}));

vi.mock('../stores/auth.store', () => ({
  useAuthStore: { getState: () => authStoreState },
}));

vi.mock('../stores/ui.store', () => ({
  useUIStore: { getState: () => ({ addToast: mockAddToast }) },
}));

describe('403 interceptor — BUG-008/BUG-009 spurious permission errors', () => {
  beforeEach(() => {
    mockAddToast.mockClear();
    authStoreState.accessToken = null;
    authStoreState.tenantId = null;
  });

  it('should NOT show a toast when the request has no Authorization token (timing race)', async () => {
    authStoreState.accessToken = null;
    server.use(http.get('/api/v1/orders', () => new HttpResponse(null, { status: 403 })));

    await expect(apiClient.get('/orders')).rejects.toBeDefined();

    expect(mockAddToast).not.toHaveBeenCalled();
  });

  it('should NOT show a toast for /admin 403s — RoleRoute handles navigation permission checks', async () => {
    authStoreState.accessToken = 'test-token';
    server.use(http.get('/api/v1/admin/users', () => new HttpResponse(null, { status: 403 })));

    await expect(apiClient.get('/admin/users')).rejects.toBeDefined();

    expect(mockAddToast).not.toHaveBeenCalled();
  });

  it('should NOT show a toast for /seller 403s — RoleRoute handles navigation permission checks', async () => {
    authStoreState.accessToken = 'test-token';
    server.use(http.get('/api/v1/seller/products', () => new HttpResponse(null, { status: 403 })));

    await expect(apiClient.get('/seller/products')).rejects.toBeDefined();

    expect(mockAddToast).not.toHaveBeenCalled();
  });

  it('should show a toast with PERMISSION_TOAST_ID for real 403s (authenticated, non-nav endpoint)', async () => {
    authStoreState.accessToken = 'test-token';
    server.use(http.get('/api/v1/orders', () => new HttpResponse(null, { status: 403 })));

    await expect(apiClient.get('/orders')).rejects.toBeDefined();

    expect(mockAddToast).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'permission-denied', variant: 'error' })
    );
  });

  it('should reject with status 403 for no-token 403 responses (no toast shown)', async () => {
    authStoreState.accessToken = null;
    server.use(http.get('/api/v1/orders', () => new HttpResponse(null, { status: 403 })));

    const error = await apiClient.get('/orders').catch((e) => e);

    expect(error).toMatchObject({ status: 403 });
    expect(mockAddToast).not.toHaveBeenCalled();
  });

  it('should reject with status 403 for nav-check 403 responses (no toast shown)', async () => {
    authStoreState.accessToken = 'test-token';
    server.use(http.get('/api/v1/admin/users', () => new HttpResponse(null, { status: 403 })));

    const error = await apiClient.get('/admin/users').catch((e) => e);

    expect(error).toMatchObject({ status: 403 });
    expect(mockAddToast).not.toHaveBeenCalled();
  });
});
