import apiClient from './client';
import type {
  AccountResponse,
  AccountUpdateResponse,
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  SellerProfileRequest,
  SellerProfileResponse,
  UpdateAccountRequest,
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

  // Forgot-password flow (role-agnostic — Customer, Seller, Admin). The request always
  // resolves 200 with a generic message regardless of whether the email exists.
  forgotPassword: (email: string) =>
    apiClient
      .post<{ message: string }>('/auth/forgot-password', { email })
      .then((r) => r.data),

  resetPassword: (data: { token: string; newPassword: string; confirmPassword: string }) =>
    apiClient
      .post<{ message: string }>('/auth/reset-password', data)
      .then((r) => r.data),

  // Account settings — the LOGIN identity (auth-service), not the customer profile.
  getAccount: () =>
    apiClient.get<AccountResponse>('/auth/account/me').then((r) => r.data),

  updateAccount: (data: UpdateAccountRequest) =>
    apiClient.patch<AccountUpdateResponse>('/auth/account/me', data).then((r) => r.data),

  changePassword: (data: ChangePasswordRequest) =>
    apiClient
      .post<AuthResponse>('/auth/account/change-password', data)
      .then((r) => r.data),

  deleteAccount: (password: string) =>
    apiClient.delete('/auth/account/me', { data: { password } }).then(() => undefined),

  // Seller business profile ("sold by" identity on invoices).
  // getSeller is readable by any authenticated user (a buyer sees who they bought from).
  getSeller: (sellerId: number | string, signal?: AbortSignal) =>
    apiClient
      .get<SellerProfileResponse>(`/auth/sellers/${sellerId}`, { signal })
      .then((r) => r.data),

  updateSellerProfile: (data: SellerProfileRequest) =>
    apiClient.put<SellerProfileResponse>('/auth/sellers/me', data).then((r) => r.data),
};
