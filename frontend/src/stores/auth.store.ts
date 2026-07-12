import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { Role, SellerStatus } from '../api/types';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null;
  email: string | null;
  role: Role | null;
  tenantId: string | null;
  /** Only sellers carry a status; null for other roles (and for pre-Fase-2 sessions). */
  sellerStatus: SellerStatus | null;
  isAuthenticated: boolean;

  setAuth: (payload: {
    accessToken: string;
    refreshToken: string;
    userId: string;
    email: string;
    role: Role;
    tenantId: string;
    sellerStatus?: SellerStatus | null;
  }) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
  setEmail: (email: string) => void;
  /** Update the seller-approval status in place (driven by the live status poll). */
  setSellerStatus: (sellerStatus: SellerStatus | null) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      email: null,
      role: null,
      tenantId: null,
      sellerStatus: null,
      isAuthenticated: false,

      setAuth: ({ accessToken, refreshToken, userId, email, role, tenantId, sellerStatus }) =>
        set({
          accessToken,
          refreshToken,
          userId,
          email,
          role,
          tenantId,
          sellerStatus: sellerStatus ?? null,
          isAuthenticated: true,
        }),

      setTokens: (accessToken, refreshToken) =>
        set({ accessToken, refreshToken }),

      setEmail: (email) => set({ email }),

      setSellerStatus: (sellerStatus) => set({ sellerStatus: sellerStatus ?? null }),

      clearAuth: () =>
        set({
          accessToken: null,
          refreshToken: null,
          userId: null,
          email: null,
          role: null,
          tenantId: null,
          sellerStatus: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: 'obsidian-auth',
      // sessionStorage is tab-scoped: two browser tabs can hold different roles
      // without poisoning each other via the shared localStorage key.
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        userId: state.userId,
        email: state.email,
        role: state.role,
        tenantId: state.tenantId,
        sellerStatus: state.sellerStatus,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
