import apiClient from './client';
import type {
  CategoryResponse,
  PageResponse,
  ProductRequest,
  ProductResponse,
  ProductPurchaseRequest,
  ProductPurchaseResponse,
} from './types';

export interface SearchParams {
  query?: string;
  categoryId?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  page?: number;
  size?: number;
}

export const productsApi = {
  getAll: (page = 0, size = 20) =>
    apiClient
      .get<PageResponse<ProductResponse>>('/products', { params: { page, size } })
      .then((r) => r.data),

  getById: (id: number) =>
    apiClient.get<ProductResponse>(`/products/${id}`).then((r) => r.data),

  create: (data: ProductRequest) =>
    apiClient.post<ProductResponse>('/products/create', data).then((r) => r.data),

  createBatch: (data: ProductRequest[]) =>
    apiClient.post<ProductResponse[]>('/products/batch', data).then((r) => r.data),

  update: (id: number, data: ProductRequest) =>
    apiClient.put<ProductResponse>(`/products/update/${id}`, data).then((r) => r.data),

  delete: (id: number) => apiClient.delete(`/products/delete/${id}`).then(() => undefined),

  purchase: (items: ProductPurchaseRequest[]) =>
    apiClient.post<ProductPurchaseResponse[]>('/products/purchase', items).then((r) => r.data),

  getCategories: () =>
    apiClient.get<CategoryResponse[]>('/products/categories').then((r) => r.data),

  search: (params: SearchParams) =>
    apiClient
      .get<PageResponse<ProductResponse>>('/products/search', { params })
      .then((r) => r.data),
};
