import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type ThemeMode = 'dark' | 'light';
export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: string;
  message: string;
  variant: ToastVariant;
  duration?: number;
}

interface UIState {
  themeMode: ThemeMode;
  sidebarOpen: boolean;
  toastQueue: Toast[];

  setThemeMode: (mode: ThemeMode) => void;
  toggleTheme: () => void;
  setSidebarOpen: (open: boolean) => void;
  toggleSidebar: () => void;
  addToast: (toast: Omit<Toast, 'id'> & { id?: string }) => string;
  removeToast: (id: string) => void;
}

export const useUIStore = create<UIState>()(
  persist(
    (set) => ({
      themeMode: 'dark',
      sidebarOpen: true,
      toastQueue: [],

      setThemeMode: (mode) => set({ themeMode: mode }),
      toggleTheme: () =>
        set((s) => ({ themeMode: s.themeMode === 'dark' ? 'light' : 'dark' })),

      setSidebarOpen: (open) => set({ sidebarOpen: open }),
      toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),

      addToast: (toast) => {
        const id = toast.id ?? `toast-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        set((s) => ({
          // Replace existing toast with the same ID; otherwise append
          toastQueue: s.toastQueue.some((t) => t.id === id)
            ? s.toastQueue.map((t) => (t.id === id ? { ...toast, id } : t))
            : [...s.toastQueue, { ...toast, id }],
        }));
        return id;
      },

      removeToast: (id) =>
        set((s) => ({ toastQueue: s.toastQueue.filter((t) => t.id !== id) })),
    }),
    {
      name: 'obsidian-ui',
      partialize: (state) => ({ themeMode: state.themeMode }),
    }
  )
);
