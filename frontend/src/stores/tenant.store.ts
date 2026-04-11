import { create } from 'zustand';
import type { FeatureFlagResponse, TenantResponse } from '../api/types';

interface TenantState {
  activeTenant: TenantResponse | null;
  featureFlags: FeatureFlagResponse[];

  setActiveTenant: (tenant: TenantResponse) => void;
  setFeatureFlags: (flags: FeatureFlagResponse[]) => void;
  isFeatureEnabled: (flagName: string) => boolean;
  clearTenant: () => void;
}

export const useTenantStore = create<TenantState>()((set, get) => ({
  activeTenant: null,
  featureFlags: [],

  setActiveTenant: (tenant) => set({ activeTenant: tenant }),
  setFeatureFlags: (flags) => set({ featureFlags: flags }),

  isFeatureEnabled: (flagName) => {
    const flag = get().featureFlags.find((f) => f.flagName === flagName);
    return flag?.enabled ?? false;
  },

  clearTenant: () => set({ activeTenant: null, featureFlags: [] }),
}));
