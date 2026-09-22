'use client';

import { useToast } from '@/hooks/useToast';
import { Toast, ToastClose, ToastDescription, ToastProvider, ToastTitle, ToastViewport } from '@/components/ui/toast';

export function Toaster() {
  const { toasts } = useToast();

  return (
    <ToastProvider>
      {toasts.map((toast) => (
        <Toast key={toast.id} type={toast.type}>
          <div className="grid gap-1">
            <ToastTitle className="font-semibold">{toast.title}</ToastTitle>
            {toast.message && <ToastDescription className="text-sm text-muted-foreground">{toast.message}</ToastDescription>}
          </div>
          <ToastClose />
        </Toast>
      ))}
      <ToastViewport />
    </ToastProvider>
  );
}