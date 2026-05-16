import apiClient from './client';
import type { Role, PageResponse } from './types';

export interface AdminUser {
  id: number;
  email: string;
  firstname: string;
  lastname: string;
  role: Role;
  tenantId: string;
  accountEnabled: boolean;
}

export const usersApi = {
  list: (page = 0, size = 25, signal?: AbortSignal) =>
    apiClient
      .get<PageResponse<AdminUser>>(`/auth/users`, { params: { page, size }, signal })
      .then((r) => r.data),

  updateRole: (userId: number, role: Role) =>
    apiClient
      .patch<AdminUser>(`/auth/users/${userId}/role`, { role })
      .then((r) => r.data),
};
