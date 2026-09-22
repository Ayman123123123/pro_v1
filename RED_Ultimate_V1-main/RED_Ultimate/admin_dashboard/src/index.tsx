import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from '@tanstack/react-router';
import { router } from './router';
import './styles.css';

// ✅ 2026-09-22: نقطة الدخول على النسخ الجديدة (TanStack Router)
// — الهيكل الحديث مع routeTree المولّد تلقائياً من src/routes/*
// App.tsx يبقى متاحاً كهيكل بديل (يحتوي صفحات antd إضافية)
if (import.meta.hot) {
  import.meta.hot.accept('./router', () => {});
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>
);
