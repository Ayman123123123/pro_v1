import { Outlet, createRootRoute } from '@tanstack/react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// ✅ FIX 2026-09-22: الملف المفقود الذي أوقف تُوليد routeTree.gen.ts بالكامل.
// بدون __root.tsx يرفض @tanstack/router-cli توليد الشجرة → الشاشة كانت فارغة.
// هذا الجذر يغلّف التطبيق بكل الـ providers التي تحتاجها النسخ الجديدة:
//   QueryClient (react-query) + Theme + Auth + Sidebar + Socket + i18n
import '../i18n';
import { ThemeProvider } from '@/components/providers/ThemeProvider';
import { AuthProvider } from '@/components/providers/AuthProvider';
import { SidebarProvider } from '@/components/providers/SidebarProvider';
import { SocketProvider } from '@/components/providers/SocketProvider';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export const Route = createRootRoute({
  // ✅ 2026-09-23: شاشة خطأ أنيقة بدل الشاشة البيضاء عند أي خطأ render
  errorComponent: ({ error }) => (
    <div dir="rtl" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0b0f17', color: '#e5e7eb', fontFamily: 'system-ui, sans-serif', padding: 24 }}>
      <div style={{ maxWidth: 520, textAlign: 'center' }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>⚠️</div>
        <h1 style={{ fontSize: 20, marginBottom: 8 }}>حدث خطأ غير متوقع</h1>
        <p style={{ color: '#9ca3af', fontSize: 14, marginBottom: 4 }}>
          عادةً يحلّ التحديث القسري للمشكلة:
          <strong style={{ color: '#e5e7eb' }}> Ctrl+Shift+R</strong> (أو <strong style={{ color: '#e5e7eb' }}>Cmd+Shift+R</strong>)
        </p>
        <pre style={{ background: '#111827', color: '#f87171', fontSize: 12, padding: 12, borderRadius: 8, overflow: 'auto', textAlign: 'left', direction: 'ltr', maxHeight: 140 }}>
          {(error as Error)?.message ?? String(error)}
        </pre>
        <button
          onClick={() => window.location.reload()}
          style={{ marginTop: 12, background: '#2563eb', color: 'white', border: 'none', padding: '10px 24px', borderRadius: 8, fontSize: 14, cursor: 'pointer' }}
        >
          إعادة تحميل التطبيق
        </button>
      </div>
    </div>
  ),
  component: () => (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <SidebarProvider>
            <SocketProvider>
              <Outlet />
            </SocketProvider>
          </SidebarProvider>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  ),
});
