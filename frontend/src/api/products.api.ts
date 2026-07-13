import apiClient from './client';
import type {
  CategoryResponse,
  PageResponse,
  ProductRequest,
  ProductResponse,
  ProductPurchaseRequest,
  ProductPurchaseResponse,
  ProductStatus,
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
  getAll: (page = 0, size = 20, signal?: AbortSignal) =>
    apiClient
      .get<PageResponse<ProductResponse>>('/products', { params: { page, size }, signal })
      .then((r) => r.data),

  // Seller's own products only (scoped by created_by on the backend). Newest-first.
  // Used by the seller management screens; the public catalogue uses getAll/search.
  getMine: (page = 0, size = 20, signal?: AbortSignal) =>
    apiClient
      .get<PageResponse<ProductResponse>>('/products/mine', { params: { page, size }, signal })
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

  // Fase 3 (admin): full catalogue including SUSPENDED products — the public
  // getAll/search endpoints only ever return ACTIVE ones.
  getAllAdmin: (page = 0, size = 20, signal?: AbortSignal) =>
    apiClient
      .get<PageResponse<ProductResponse>>('/products/admin', { params: { page, size }, signal })
      .then((r) => r.data),

  // Fase 3 (admin): suspend / reactivate a product.
  setStatus: (id: number, status: ProductStatus) =>
    apiClient
      .patch<ProductResponse>(`/products/${id}/status`, { status })
      .then((r) => r.data),

  search: (params: SearchParams) =>
    apiClient
      .get<PageResponse<ProductResponse>>('/products/search', { params })
      .then((r) => r.data),
};
