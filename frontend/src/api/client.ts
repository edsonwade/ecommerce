import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { AppError, ApiError } from './types';

const PUBLIC_PATHS = ['/auth/login', '/auth/register', '/auth/refresh'];

export const PERMISSION_TOAST_ID = 'permission-denied';

// Singleton mutex to prevent concurrent refresh attempts
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token!);
  });
  failedQueue = [];
}

// Lazy store accessors — avoid circular import at module init time.
// Cached after first import so concurrent requests share the same reference.
let cachedAuthStore: typeof import('../stores/auth.store').useAuthStore | null = null;
let cachedUIStore: typeof import('../stores/ui.store').useUIStore | null = null;

async function getAuthStore() {
  if (!cachedAuthStore) {
    const m = await import('../stores/auth.store');
    cachedAuthStore = m.useAuthStore;
  }
  return cachedAuthStore;
}

async function getUIStore() {
  if (!cachedUIStore) {
    const m = await import('../stores/ui.store');
    cachedUIStore = m.useUIStore;
  }
  return cachedUIStore;
}

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// ── Request Interceptor ──
apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const isPublicPath = PUBLIC_PATHS.some((p) => config.url?.includes(p));

  if (!isPublicPath) {
    const useAuthStore = await getAuthStore();
    const { accessToken, tenantId } = useAuthStore.getState();

    if (accessToken) {
      config.headers['Authorization'] = `Bearer ${accessToken}`;
    }
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId;
    }
  }

  return config;
});

// ── Response Interceptor — Silent Token Refresh ──
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    const isPublicPath = PUBLIC_PATHS.some((p) => originalRequest.url?.includes(p));

    if (error.response?.status === 401 && !originalRequest._retry && !isPublicPath) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers['Authorization'] = `Bearer ${token}`;
          return apiClient(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const useAuthStore = await getAuthStore();
      const { refreshToken, clearAuth } = useAuthStore.getState();

      try {
        const response = await axios.post('/api/v1/auth/refresh', null, {
          headers: { Authorization: `Bearer ${refreshToken}` },
        });
        const { accessToken: newAccessToken, refreshToken: newRefreshToken } = response.data;
        useAuthStore.getState().setTokens(newAccessToken, newRefreshToken);
        processQueue(null, newAccessToken);
        originalRequest.headers['Authorization'] = `Bearer ${newAccessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        clearAuth();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    if (error.response?.status === 403) {
      console.log('[403]', originalRequest.url, originalRequest.headers?.['Authorization']);
      const url = originalRequest.url ?? '';

      // Don't toast for requests fired before auth was ready (timing issue, no real permission error)
      const hasToken = originalRequest.headers?.['Authorization'];
      if (!hasToken) {
        return Promise.reject(normalizeError(error));
      }

      // Don't toast for navigation-triggered 403s — role route guards handle these
      const isNavigationCheck = url.includes('/admin') || url.includes('/seller');
      if (isNavigationCheck) {
        return Promise.reject(normalizeError(error));
      }

      const useUIStore = await getUIStore();
      useUIStore.getState().addToast({
        id: PERMISSION_TOAST_ID,
        message: "You don't have permission to perform this action",
        variant: 'error',
      });
    }

    return Promise.reject(normalizeError(error));
  }
);

// Strip internal reference IDs from error messages
function sanitizeMessage(msg: string): string {
  return msg.replace(/\s*Reference:\s*\[?[\w-]+\]?/gi, '').trim();
}

export function normalizeError(error: unknown): AppError {
  if (error && typeof error === 'object' && 'status' in error && 'message' in error) {
    const appErr = error as AppError;
    return { ...appErr, message: sanitizeMessage(appErr.message) };
  }
  if (axios.isAxiosError(error)) {
    const status = error.response?.status ?? 0;
    const data = error.response?.data as ApiError | undefined;

    if (status === 400 && data?.errors) {
      return { status, message: 'Validation failed', fieldErrors: data.errors };
    }
    if (status === 403) return { status, message: 'Insufficient permissions' };
    if (status === 404) return { status, message: 'Resource not found' };
    if (status === 409) return { status, message: 'Conflict — resource already exists' };
    if (status === 422) return { status, message: 'Unprocessable — insufficient inventory' };
    if (status === 429) return { status, message: 'Rate limited — please try again shortly' };
    if (status === 503) return { status, message: 'Service unavailable — please try again later' };

    const rawMessage =
      (error.response?.data as { message?: string })?.message ?? error.message ?? 'An unexpected error occurred';
    return {
      status,
      message: sanitizeMessage(rawMessage),
    };
  }
  return { status: 0, message: 'Network error — check your connection' };
}

export default apiClient;
