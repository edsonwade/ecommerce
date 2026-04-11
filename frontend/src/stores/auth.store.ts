import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Role } from '../api/types';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null;
  email: string | null;
  role: Role | null;
  tenantId: string | null;
  isAuthenticated: boolean;

  setAuth: (payload: {
    accessToken: string;
    refreshToken: string;
    userId: string;
    email: string;
    role: Role;
    tenantId: string;
  }) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
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
      isAuthenticated: false,

      setAuth: ({ accessToken, refreshToken, userId, email, role, tenantId }) =>
        set({ accessToken, refreshToken, userId, email, role, tenantId, isAuthenticated: true }),

      setTokens: (accessToken, refreshToken) =>
        set({ accessToken, refreshToken }),

      clearAuth: () =>
        set({
          accessToken: null,
          refreshToken: null,
          userId: null,
          email: null,
          role: null,
          tenantId: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: 'obsidian-auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        userId: state.userId,
        email: state.email,
        role: state.role,
        tenantId: state.tenantId,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
