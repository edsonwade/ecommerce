import apiClient from './client';
import type { AuthResponse, LoginRequest, RegisterRequest } from './types';

export const authApi = {
  register: (data: RegisterRequest) =>
    apiClient.post<AuthResponse>('/auth/register', data).then((r) => r.data),

  login: (data: LoginRequest) =>
    apiClient.post<AuthResponse>('/auth/login', data).then((r) => r.data),

  refresh: (refreshToken: string) =>
    apiClient
      .post<AuthResponse>('/auth/refresh', null, {
        headers: { Authorization: `Bearer ${refreshToken}` },
      })
      .then((r) => r.data),

  logout: () => apiClient.post('/auth/logout').then(() => undefined),
};
