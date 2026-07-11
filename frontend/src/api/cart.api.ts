import apiClient from './client';
import type { AddCartItemRequest, CartResponse } from './types';

export const cartApi = {
  get: (customerId: string) =>
    apiClient.get<CartResponse>(`/carts/${customerId}`).then((r) => r.data),

  // idempotencyKey: a stable per-click UUID. addItem is a RELATIVE mutation
  // (it increments the quantity) and the add-to-cart mutations retry on 503 —
  // the same false-503-on-a-succeeded-write pattern that once duplicated orders.
  // Sending the same key on the retry lets cart-service return the current cart
  // instead of incrementing the quantity again.
  addItem: (customerId: string, data: AddCartItemRequest, idempotencyKey?: string) =>
    apiClient
      .post<CartResponse>(
        `/carts/${customerId}/items`,
        data,
        idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined,
      )
      .then((r) => r.data),

  updateQuantity: (customerId: string, productId: number, quantity: number) =>
    apiClient
      .patch<CartResponse>(`/carts/${customerId}/items/${productId}`, null, {
        params: { quantity },
      })
      .then((r) => r.data),

  removeItem: (customerId: string, productId: number) =>
    apiClient
      .delete<CartResponse>(`/carts/${customerId}/items/${productId}`)
      .then((r) => r.data),

  clear: (customerId: string) =>
    apiClient.delete(`/carts/${customerId}`).then(() => undefined),

  checkout: (customerId: string) =>
    apiClient.get<CartResponse>(`/carts/${customerId}/checkout`).then((r) => r.data),
};
