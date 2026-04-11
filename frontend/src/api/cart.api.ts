import apiClient from './client';
import type { AddCartItemRequest, CartResponse } from './types';

export const cartApi = {
  get: (customerId: string) =>
    apiClient.get<CartResponse>(`/carts/${customerId}`).then((r) => r.data),

  addItem: (customerId: string, data: AddCartItemRequest) =>
    apiClient.post<CartResponse>(`/carts/${customerId}/items`, data).then((r) => r.data),

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
