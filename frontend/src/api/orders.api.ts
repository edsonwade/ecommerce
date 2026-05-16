import apiClient from './client';
import type {
  OrderCreateResponse,
  OrderLineResponse,
  OrderRequest,
  OrderResponse,
  OrderStatusResponse,
} from './types';

export const ordersApi = {
  create: (data: OrderRequest) =>
    apiClient.post<OrderCreateResponse>('/orders', data).then((r) => r.data),

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
};
