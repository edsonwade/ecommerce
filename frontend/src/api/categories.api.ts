import apiClient from './client';
import type { CategoryRequest, CategoryResponse } from './types';

/**
 * Fase 4: category administration against the dedicated /categories endpoint.
 * GET is public; create/update/delete are ADMIN-only (enforced server-side).
 * The public catalogue dropdown keeps using productsApi.getCategories()
 * (/products/categories) — both return the same CategoryResponse shape.
 */
export const categoriesApi = {
  getAll: (signal?: AbortSignal) =>
    apiClient.get<CategoryResponse[]>('/categories', { signal }).then((r) => r.data),

  create: (data: CategoryRequest) =>
    apiClient.post<CategoryResponse>('/categories', data).then((r) => r.data),

  update: (id: number, data: CategoryRequest) =>
    apiClient.put<CategoryResponse>(`/categories/${id}`, data).then((r) => r.data),

  remove: (id: number) => apiClient.delete(`/categories/${id}`).then(() => undefined),
};
