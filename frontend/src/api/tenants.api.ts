import apiClient from './client';
import type {
  CreateTenantRequest,
  FeatureFlagResponse,
  RecordUsageRequest,
  SetFeatureFlagRequest,
  TenantPlan,
  TenantResponse,
  UpdateTenantRequest,
  UsageMetricResponse,
} from './types';

export const tenantsApi = {
  getAll: () => apiClient.get<TenantResponse[]>('/tenants').then((r) => r.data),

  getById: (id: string) =>
    apiClient.get<TenantResponse>(`/tenants/${id}`).then((r) => r.data),

  getBySlug: (slug: string) =>
    apiClient.get<TenantResponse>(`/tenants/by-slug/${slug}`).then((r) => r.data),

  create: (data: CreateTenantRequest) =>
    apiClient.post<TenantResponse>('/tenants', data).then((r) => r.data),

  update: (id: string, data: UpdateTenantRequest) =>
    apiClient.put<TenantResponse>(`/tenants/${id}`, data).then((r) => r.data),

  changePlan: (id: string, plan: TenantPlan) =>
    apiClient
      .patch<TenantResponse>(`/tenants/${id}/plan`, null, { params: { plan } })
      .then((r) => r.data),

  suspend: (id: string) =>
    apiClient.patch(`/tenants/${id}/suspend`).then(() => undefined),

  reactivate: (id: string) =>
    apiClient.patch(`/tenants/${id}/reactivate`).then(() => undefined),

  delete: (id: string) => apiClient.delete(`/tenants/${id}`).then(() => undefined),

  getFlags: (id: string) =>
    apiClient.get<FeatureFlagResponse[]>(`/tenants/${id}/flags`).then((r) => r.data),

  setFlag: (id: string, name: string, data: SetFeatureFlagRequest) =>
    apiClient
      .put<FeatureFlagResponse>(`/tenants/${id}/flags/${name}`, data)
      .then((r) => r.data),

  getFlagStatus: (id: string, name: string) =>
    apiClient
      .get<boolean>(`/tenants/${id}/flags/${name}/status`)
      .then((r) => r.data),

  recordUsage: (id: string, data: RecordUsageRequest) =>
    apiClient
      .post<UsageMetricResponse>(`/tenants/${id}/usage`, data)
      .then((r) => r.data),

  getUsage: (id: string, date: string) =>
    apiClient
      .get<UsageMetricResponse[]>(`/tenants/${id}/usage`, { params: { date } })
      .then((r) => r.data),

  getUsageRange: (id: string, startDate: string, endDate: string) =>
    apiClient
      .get<UsageMetricResponse[]>(`/tenants/${id}/usage/range`, {
        params: { startDate, endDate },
      })
      .then((r) => r.data),

  getUsageSum: (id: string, metricName: string, startDate: string, endDate: string) =>
    apiClient
      .get<number>(`/tenants/${id}/usage/sum`, {
        params: { metricName, startDate, endDate },
      })
      .then((r) => r.data),
};
