import apiClient from './client';
import type { PaymentResponse } from './types';

export const paymentsApi = {
  getAll: () => apiClient.get<PaymentResponse[]>('/payments').then((r) => r.data),

  getById: (id: number) =>
    apiClient.get<PaymentResponse>(`/payments/${id}`).then((r) => r.data),
};
