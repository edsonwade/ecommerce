import apiClient from './client';
import type {
  OrderCreateResponse,
  OrderLineResponse,
  OrderRequest,
  OrderResponse,
  OrderStatusResponse,
} from './types';

export const ordersApi = {
  // idempotencyKey: a stable per-checkout UUID. The gateway can return a false 503
  // on a write that actually succeeded (stale keep-alive connection); sending the
  // same key on a resubmit lets order-service return the existing order instead of
  // creating a duplicate.
  create: (data: OrderRequest, idempotencyKey?: string) =>
    apiClient
      .post<OrderCreateResponse>(
        '/orders',
        data,
        idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined,
      )
      .then((r) => r.data),

  getStatus: (correlationId: string) =>
    apiClient
      .get<OrderStatusResponse>(`/orders/status/${correlationId}`)
      .then((r) => r.data),

  getAll: (signal?: AbortSignal) =>
    apiClient.get<OrderResponse[]>('/orders', { signal }).then((r) => r.data),

  getById: (orderId: number, signal?: AbortSignal) =>
    apiClient.get<OrderResponse>(`/orders/${orderId}`, { signal }).then((r) => r.data),

  getLines: (orderId: number, signal?: AbortSignal) =>
    apiClient.get<OrderLineResponse[]>(`/order-lines/${orderId}`, { signal }).then((r) => r.data),

  getMyOrders: (signal?: AbortSignal) =>
    apiClient.get<OrderResponse[]>('/orders/my', { signal }).then((r) => r.data),
};
