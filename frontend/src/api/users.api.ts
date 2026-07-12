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

/** POST /auth/users — mirrors backend AdminCreateUserRequest (tenantId optional, defaults "default"). */
export interface AdminCreateUserPayload {
  firstname: string;
  lastname: string;
  email: string;
  password: string;
  role: Role;
  tenantId?: string;
}

/** PATCH /auth/users/{id} — partial update; backend requires at least one field. */
export interface AdminUpdateUserPayload {
  firstname?: string;
  lastname?: string;
  email?: string;
}

export const usersApi = {
  list: (page = 0, size = 25, signal?: AbortSignal) =>
    apiClient
      .get<PageResponse<AdminUser>>(`/auth/users`, { params: { page, size }, signal })
      .then((r) => r.data),

  create: (payload: AdminCreateUserPayload) =>
    apiClient
      .post<AdminUser>(`/auth/users`, payload)
      .then((r) => r.data),

  update: (userId: number, payload: AdminUpdateUserPayload) =>
    apiClient
      .patch<AdminUser>(`/auth/users/${userId}`, payload)
      .then((r) => r.data),

  setStatus: (userId: number, enabled: boolean) =>
    apiClient
      .patch<AdminUser>(`/auth/users/${userId}/status`, { enabled })
      .then((r) => r.data),

  remove: (userId: number) =>
    apiClient
      .delete<void>(`/auth/users/${userId}`)
      .then(() => undefined),

  updateRole: (userId: number, role: Role) =>
    apiClient
      .patch<AdminUser>(`/auth/users/${userId}/role`, { role })
      .then((r) => r.data),
};
