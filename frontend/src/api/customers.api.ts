import apiClient from './client';
import type { CustomerRequest, CustomerResponse } from './types';

export const customersApi = {
  getAll: () => apiClient.get<CustomerResponse[]>('/customers').then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<CustomerResponse>(`/customers/${id}`).then((r) => r.data),

  getByEmail: (email: string) =>
    apiClient
      .get<CustomerResponse>('/customers/by-email', { params: { address: email } })
      .then((r) => r.data),

  create: (data: CustomerRequest) =>
    apiClient.post<string>('/customers', data).then((r) => r.data),

  update: (id: string, data: CustomerRequest) =>
    apiClient.put<CustomerResponse>(`/customers/${id}`, data).then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/customers/${id}`).then(() => undefined),
};
