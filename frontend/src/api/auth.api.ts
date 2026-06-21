import apiClient from './client';
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  SellerProfileRequest,
  SellerProfileResponse,
} from './types';

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

  // Seller business profile ("sold by" identity on invoices).
  // getSeller is readable by any authenticated user (a buyer sees who they bought from).
  getSeller: (sellerId: number | string, signal?: AbortSignal) =>
    apiClient
      .get<SellerProfileResponse>(`/auth/sellers/${sellerId}`, { signal })
      .then((r) => r.data),

  updateSellerProfile: (data: SellerProfileRequest) =>
    apiClient.put<SellerProfileResponse>('/auth/sellers/me', data).then((r) => r.data),
};
