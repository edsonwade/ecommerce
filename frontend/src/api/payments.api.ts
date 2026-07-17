import apiClient from './client';
import type { PaymentResponse } from './types';

export const paymentsApi = {
  getAll: () => apiClient.get<PaymentResponse[]>('/payments').then((r) => r.data),

  getById: (id: number) =>
    apiClient.get<PaymentResponse>(`/payments/${id}`).then((r) => r.data),

  // Fase 6 — ADMIN, one-shot: refunding an already-REFUNDED payment returns 409.
  refund: (id: number) =>
    apiClient.post<PaymentResponse>(`/payments/${id}/refund`).then((r) => r.data),
};
