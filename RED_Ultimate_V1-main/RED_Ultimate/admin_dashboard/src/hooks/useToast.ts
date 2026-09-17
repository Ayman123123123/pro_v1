'use client';

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

interface Toast {
  id: string;
  type: 'default' | 'success' | 'error' | 'warning' | 'info';
  title: string;
  message?: string;
  duration?: number;
  action?: { label: string; onClick: () => void };
}

interface ToastState {
  toasts: Toast[];
  addToast: (toast: Omit<Toast, 'id'>) => string;
  removeToast: (id: string) => void;
}

export const useToastStore = create<ToastState>()(
  persist(
    (set) => ({
      toasts: [],
      addToast: (toast) => {
        const id = crypto.randomUUID();
        set((state) => ({ toasts: [...state.toasts, { ...toast, id }] }));
        if (toast.duration !== 0) {
          setTimeout(() => {
            set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
          }, toast.duration || 5000);
        }
        return id;
      },
      removeToast: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
    }),
    {
      name: 'toast-storage',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ toasts: [] }),
    }
  )
);

export function useToast() {
  return useToastStore();
}

export function toast(options: Omit<Toast, 'id'>) {
  return useToastStore.getState().addToast(options);
}

toast.success = (title: string, message?: string, options?: Partial<Toast>) =>
  toast({ type: 'success', title, message, ...options });

toast.error = (title: string, message?: string, options?: Partial<Toast>) =>
  toast({ type: 'error', title, message, ...options });

toast.warning = (title: string, message?: string, options?: Partial<Toast>) =>
  toast({ type: 'warning', title, message, ...options });

toast.info = (title: string, message?: string, options?: Partial<Toast>) =>
  toast({ type: 'info', title, message, ...options });

toast.default = (title: string, message?: string, options?: Partial<Toast>) =>
  toast({ type: 'default', title, message, ...options });