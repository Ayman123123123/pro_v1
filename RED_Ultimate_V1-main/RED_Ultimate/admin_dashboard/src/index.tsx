import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from '@tanstack/react-router';
import { router } from './router';
import './styles.css';
import './i18n';

// ✅ 2026-09-22: نقطة الدخول على النسخ الجديدة (TanStack Router)
// — الهيكل الحديث مع routeTree المولّد تلقائياً من src/routes/*
// App.tsx يبقى متاحاً كهيكل بديل (يحتوي صفحات antd إضافية)
// ✅ 2026-09-24: مزامنة dir/lang مع i18n (ar=rtl) قبل أول render — يمنع وميض LTR
import i18n from './i18n';
function syncDir() {
  const lng = String(i18n.language || 'ar');
  const rtl = lng.startsWith('ar');
  document.documentElement.lang = lng.split('-')[0];
  document.documentElement.dir = rtl ? 'rtl' : 'ltr';
}
syncDir();
i18n.on('languageChanged', syncDir);
if (import.meta.hot) {
  import.meta.hot.accept('./router', () => {});
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>
);
